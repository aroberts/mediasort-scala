package mediasort

import cats.data.OptionT
import mediasort.classify.{Classification, Classifier, Input}
import mediasort.config.{CLIArgs, Config}
import mediasort.clients.{Clients, Logging}
import cats.effect._
import cats.syntax.show._
import mediasort.errors._
import fs2.Stream
import fs2.io.Watcher.Event
import fs2.io.Watcher.EventType._
import mediasort.action.Action

import java.nio.file.{Path, Paths}

object Mediasort extends IOApp {
  implicit val cs: ContextShift[IO] = contextShift

  def version = Option(getClass.getPackage.getImplementationVersion).getOrElse("dev")

  def program(args: CLIArgs): Stream[IO, Unit] = for {
    cfg <- Config.load[Config](args.configPath)
    clients <- Clients.fromConfig(cfg)

    // log if we're running in "watch mode"
    _ <- Stream.eval(
      OptionT(IO.pure(args.input.swap.toOption)).semiflatTap(d =>
        IO(scribe.info(s"Watching $d for files with input paths..."))
      ).value
    )

    // handle the possible input modes
    _ <- args.input match {
      case Left(watchDir) =>
        for {
          // TODO: can we detect the completion of the processing of an "event" and delete the file?
          //  add --deleteWatch flag to remove watch files after processing them
          //  - or do it by default- --no-delete-watch
          //  delete watchfiles unless an error occurs during processing or --no-delete-watch

          //  counterpoint: you're already cleaning up other watchdirs with time-based criteria,
          //  may as well just clean this one too

          // TODO: Errors in watch mode are shutting down the program
          event <- paths.watch(watchDir, Created, Modified)
          _ <- Event.pathOf(event).map(processWatchPath(_, args.dryRun, cfg, clients))
            .getOrElse(Stream.empty)
            .handleErrorWith(e => Stream.eval(errorHandler(e, 3)))

          // treat watch-generated inputs as their own "mini run" of the program, trapping any
          // errors generated. Otherwise, an error during processing a watch input would bubble up
          // to the main event loop and halt the watch stream.
        } yield ()
      case Right(inputPath) => processInputPath(inputPath, args.dryRun, cfg, clients)
    }
  } yield ()

  def extractPaths(path: Path): Stream[IO, Path] =
    Stream.eval(IO(scribe.debug(s"[WATCH]: scanning $path"))) >>
      paths.readFile(path)
        .flatMap(contents => Stream.emits(contents.split("\n")))
        .map(_.trim)
        .filter(_.nonEmpty)
        .evalTap(s => IO(scribe.debug(s"[WATCH]: found $s")))
        .map(s => Paths.get(s))

  def processWatchPath(path: Path, dryRun: Boolean, cfg: Config, clients: Clients) = for {
    inputPath <- extractPaths(path)
    _ <- processInputPath(inputPath, dryRun, cfg, clients)
    // TODO: does this get evaluated once per file or once per path? must be per path...
    _ <- Stream.eval(IO(scribe.debug("would delete file here")))
  } yield ()

  def processInputPath(input: Path, dryRun: Boolean, cfg: Config, clients: Clients) = for {
    input <- Stream.eval(Input(input))
    _ <- Stream.eval(IO(scribe.trace(s"$input")))

    classifiers = cfg.classifiers.filter(_.applies(input))
    _ <- Stream.eval(IO(scribe.debug(s"running ${classifiers.size} classifiers")))

    classifications = Classifier.classifications(input, classifiers, clients.omdb)

    classification <- Classification.merged(classifications).take(1)
    _ = scribe.debug(classification.show)

    action <- Stream.emits(cfg.actionsFor(classification))
    _ <- Stream.eval(Action.perform(action, classification, dryRun, clients.email, clients.plex))
  } yield ()

  def run(args: List[String]) = {
    CLIArgs.parser.parse(args) match {
      case Left(help) => IO(System.err.println(help)).as(ExitCode.Success)
      case Right(other) => other match {
        case Left(_) => IO(System.err.println(s"mediasort v$version")).as(ExitCode.Success)
        case Right(parsed) =>
          Logging.configure(parsed.logLevel, parsed.logPath)

          program(parsed)
            .compile
            .drain
            .as(ExitCode.Success)
            .handleErrorWith(e => errorHandler(e, 3).as(ExitCode.Error))
      }
    }
  }

  // TODO: this should be tailrec-able
  def errorHandler(e: Throwable, depth: Int): IO[Unit] =
    IO(scribe.error("[Global] " + e.show)) *>
      IO(scribe.trace("[Global]", e)) *>
      (e match {
        case ex: Exception if depth > 0 && Option(ex.getCause).isDefined => errorHandler(ex.getCause, depth - 1)
        case _ => IO.pure(())
      })

}

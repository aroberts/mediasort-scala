package mediasort

import mediasort.classify.{Classification, Classifier, Input}
import mediasort.config.{CLIArgs, Config}
import mediasort.clients.{Clients, Logging}
import cats.effect._
import cats.syntax.show._
import cats.syntax.either._
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

    _ = args.input.swap.toOption.map(d => scribe.info(s"Watching $d for files with input paths..."))

    _ <- args.input match {
      case Left(watchDir) =>
        (for {
          // TODO: can we detect the completion of the processing of an "event" and delete the file?
          //  add --deleteWatch flag to remove watch files after processing them
          //  - or do it by default- --no-delete-watch
          //  delete watchfiles unless an error occurs during processing or --no-delete-watch
          event <- paths.watch(watchDir, Created)
          inputPath <- watchForPaths(event)
          _ <- processPath(inputPath, args.dryRun, cfg, clients)

          // treat watch-generated inputs as their own "mini run" of the program, trapping any
          // errors generated. Otherwise, an error during processing a watch input would bubble up
          // to the main event loop and halt the watch stream.
        } yield ()).handleErrorWith(e => Stream.eval(errorHandler(e, 3)))
      case Right(inputPath) => processPath(inputPath, args.dryRun, cfg, clients)
    }
  } yield ()

  def watchForPaths(e: Event) = e match {
    case Event.Created(path, count) =>
      Stream.emit(IO(scribe.debug(s"checking $path for inputs..."))) >>
        paths.readFile(path)
          .flatMap(contents => Stream.emits(contents.split("\\\\n")))
          .map(_.trim)
          .filter(_.nonEmpty)
          .evalTap(s => IO(scribe.debug(s" - constructing Input from $s")))
          .map(s => Paths.get(s))

    case x => Stream.emit(IO(scribe.trace(s" ignoring $x"))) >> Stream.empty
  }

  def processPath(input: Path, dryRun: Boolean, cfg: Config, clients: Clients) = for {
    input <- Stream.eval(Input(input))
    _ = scribe.info(s"$input")

    classifiers = cfg.classifiers.filter(_.applies(input))
    _ = scribe.debug(s"running ${classifiers.size} classifiers")

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

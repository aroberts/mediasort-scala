package mediasort

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

  def inputPathProgram(input: Path, args: CLIArgs): Stream[IO, Unit] = for {
    cfg <- Config.load[Config](args.configPath)
    clients <- Clients.fromConfig(cfg)
    _ <- processInput(input, args.dryRun, cfg, clients)
  } yield ()

  def watchDirProgram(watch: Path, args: CLIArgs): Stream[IO, Unit] = for {
    cfg <- Config.load[Config](args.configPath)
    clients <- Clients.fromConfig(cfg)

    _ = scribe.info(s"Watching $watch for files with input paths...")

    event <- paths.watch(watch, Created, Deleted)
    input <- watchForInputs(event).handleErrorWith(e => Stream.eval(errorHandler(e, 3)))

    // need to "attempt" these steps
    // - input creation (can fail)
    // - processInput() call

    // basically everything should be attempted rather than allowed to bubble in "watch mode"

    // the two Program functions could be collapsed if
    // - the watch could be conditionally applied
    // - the "outer" (app level) error handling can be unified
    //   - i think it can; we want the same basic error functionality if any of the "outer" code
    //   errors
  } yield ()

  def watchForInputs(e: Event) = e match {
    case Event.Created(path, count) =>
      Stream.emit(IO(scribe.debug(s"checking $path for inputs..."))) >>
        paths.readFile(path)
          .flatMap(contents => Stream.emits(contents.split("\\\\n")))
          .map(_.trim)
          .filter(_.nonEmpty)
          .evalTap(s => IO(scribe.trace(s" - constructing Input from $s")))
          .evalMap(s => Input(Paths.get(s)))

    case x => Stream.emit(IO(println(s" ignoring $x"))) >> Stream.empty
  }

  def processInput(input: Path, dryRun: Boolean, cfg: Config, clients: Clients) = for {
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

          parsed.input match {
            case Left(watch) => watchDirProgram(watch, parsed)
              .compile
              .drain
              .as(ExitCode.Success)
              // TODO: errors will be different in watch mode
//              .handleErrorWith(e => errorHandler(e, 3).as(ExitCode.Error))

            case Right(input) => inputPathProgram(input, parsed)
              .compile
              .drain
              .as(ExitCode.Success)
              .handleErrorWith(e => errorHandler(e, 3).as(ExitCode.Error))
          }
      }
    }
  }

  // TODO: this should be tailrec-able
  def errorHandler(e: Throwable, depth: Int): IO[Unit] =
    IO(scribe.error("[Global] " + e.show)) *>
      IO(e.getStackTrace.foreach(s => scribe.trace(s.toString))) *>
      (e match {
        case ex: Exception if depth > 0 && Option(ex.getCause).isDefined => errorHandler(ex.getCause, depth - 1)
        case _ => IO.pure(())
      })

}

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
import scala.concurrent.duration._

object Mediasort extends IOApp {
  implicit val cs: ContextShift[IO] = contextShift

  def version = Option(getClass.getPackage.getImplementationVersion).getOrElse("dev")

  def program(args: CLIArgs): Stream[IO, Unit] = for {
    cfg <- Config.load[Config](args.configPath)
    clients <- Clients.fromConfig(cfg)
    _ <- Stream.eval(IO(scribe.info(s"Mediasort v${version}")))

    // handle the possible input modes
    _ <- args.input match {
      case Left(watchDir) => processWatchDir(watchDir, args.dryRun, cfg, clients)
      case Right(inputPath) => processInputPath(inputPath, args.dryRun, cfg, clients)
    }
  } yield ()

  def processWatchDir(watchDir: Path, dryRun: Boolean, cfg: Config, clients: Clients) =
    // log if we're running in "watch mode"
    Stream.eval(IO(scribe.info(s"Watching $watchDir for files with input paths..."))) >>
      paths.watch(watchDir, Created, Modified)
        .evalTap(event => IO(scribe.trace(s"EVENT: ${event.toString}")))
        .map(Event.pathOf)
        .unNone
        .evalTap(path => IO(scribe.trace(s"PATH: ${path.toString}")))
        // filter out duplicate events
        .changesBy(_.toString)
        .evalTap(path => IO(scribe.trace(s"FILTERED_PATH: ${path.toString}")))
        // look at the watch-event file, and consider each line as an input path
        .flatMap(extractPaths)
        // process each input path from the file
        .flatMap(processInputPath(_, dryRun, cfg, clients))
        // handle errors for this stream internally to avoid terminating the app
        .handleErrorWith(e => Stream.eval(errorHandler(e, 3)))
        .as(())

  def extractPaths(path: Path): Stream[IO, Path] =
    Stream.eval(IO(scribe.debug(s"[WATCH]: scanning $path for inputs"))) >>
      paths.readFile(path)
        .flatMap(contents => Stream.emits(contents.split("\n")))
        .map(_.trim)
        .filter(_.nonEmpty)
        .evalTap(s => IO(scribe.debug(s"[WATCH]: found $s")))
        .map(s => Paths.get(s))

  def processInputPath(input: Path, dryRun: Boolean, cfg: Config, clients: Clients) = for {
    input <- Stream.eval(Input(input, cfg.inputRewrite.getOrElse(List.empty)))
    _ <- Stream.eval(IO(scribe.trace(s"$input")))

    classifiers = cfg.classifiers.filter(_.applies(input))
    _ <- Stream.eval(IO(scribe.debug(s"running ${classifiers.size} classifiers")))

    classifications = Classification.merged(Classifier.classifications(input, classifiers, clients.omdb)) ++
      Stream.emit(Classification.none(input.path, cfg))

    classification <- classifications.take(1)
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

  def errorHandler(e: Throwable, depth: Int): IO[Unit] =
    IO(scribe.error("[Global] " + e.show)) *>
      IO(scribe.trace("[Global]", e)) *>
      (e match {
        case ex: Exception if depth > 0 && Option(ex.getCause).isDefined => errorHandler(ex.getCause, depth - 1)
        case _ => IO.pure(())
      })

}

package mediasort

import mediasort.classify.{Classification, Classifier, Input}
import mediasort.config.{CLIArgs, Config}
import mediasort.io.{Email, Logging, OMDB, Plex}
import cats.effect._
import cats.syntax.show._
import cats.instances.list._
import mediasort.errors._
import fs2.Stream
import cats.syntax.functor._
import mediasort.action.Action
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend

object Mediasort extends IOApp {
  implicit val cs: ContextShift[IO] = contextShift
//  val sttpClient = AsyncHttpClientCatsBackend[IO]().unsafeRunSync()


  def version = Option(getClass.getPackage.getImplementationVersion).getOrElse("dev")
  def program(args: CLIArgs): Stream[IO, Unit] = for {
    cfg <- Config.load(args.configPath)
    input <- Stream.eval(Input(args.inputPath))

    sttpClient <- Stream.resource(AsyncHttpClientCatsBackend.resource[IO]())

    // memoized apis
    omdb <- Stream.eval(memoizedAPI(cfg.omdb, OMDB(_, sttpClient), "omdb", "OMDB"))
    plex <- Stream.eval(memoizedAPI(cfg.plex, Plex(_, sttpClient), "plex", "Plex"))
    email <- Stream.eval(memoizedAPI(cfg.email, new Email(_), "email", "email notification"))

    classifiers = cfg.classifiers.filter(_.applies(input))
    _ = scribe.debug(s"running ${classifiers.size} classifiers")
    classifications = Classifier.classifications(input, classifiers, omdb)
      .map(Classification.mergeByType)

    classification <- Stream.evals(classifications)
    _ = scribe.debug(classification.show)

    action <- Stream.emits(cfg.actionsFor(classification))
    _ <- Stream.eval(Action.perform(action, classification, args.dryRun, email, plex))
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
            .handleErrorWith(e => IO(scribe.error(e.show)).as(ExitCode.Error))
      }
    }
  }

  def memoizedAPI[Cfg, Api](cfg: Option[Cfg], f: Cfg => Api, cfgName: String, apiName: String): IO[IO[Api]] =
    Async.memoize(cfg.map(f).fold[IO[Api]](
      IO.raiseError(report(s"configure $cfgName section to use $apiName capabilities"))
    )(IO.pure))
}

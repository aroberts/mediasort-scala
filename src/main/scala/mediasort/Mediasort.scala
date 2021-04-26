package mediasort

import mediasort.classify.{Classification, Classifier, Input}
import mediasort.config.{CLIArgs, Config}
import mediasort.clients.{Email, Logging, OMDB, Plex}
import cats.effect._
import cats.syntax.show._
import mediasort.errors._
import fs2.Stream
import mediasort.action.Action
import org.http4s.client.blaze._

import scala.concurrent.ExecutionContext

object Mediasort extends IOApp {
  implicit val cs: ContextShift[IO] = contextShift

  def version = Option(getClass.getPackage.getImplementationVersion).getOrElse("dev")
  def program(args: CLIArgs): Stream[IO, Unit] = for {
    cfg <- Config.load[Config](args.configPath)
    input <- Stream.eval(Input(args.inputPath))

    // TODO: more appropriate http thread pool
    httpClient <- Stream.resource(BlazeClientBuilder[IO](ExecutionContext.global).resource)

    // memoized apis
    omdb <- Stream.eval(memoizedAPI(cfg.omdb, new OMDB(_, httpClient), "omdb", "OMDB"))
    plex <- Stream.eval(memoizedAPI(cfg.plex, new Plex(_, httpClient), "plex", "Plex"))
    email <- Stream.eval(memoizedAPI(cfg.email, new Email(_), "email", "email notification"))

    classifiers = cfg.classifiers.filter(_.applies(input))
    _ = scribe.debug(s"running ${classifiers.size} classifiers")

    classifications = Classifier.classifications(input, classifiers, omdb)

    classification <- Classification.merged(classifications).take(1)
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
            .handleErrorWith(e => errorHandler(e, 3).as(ExitCode.Error))
      }
    }
  }

  def errorHandler(e: Throwable, depth: Int): IO[Unit] =
    IO(scribe.error("[Global] " + e.show)).flatMap(_ => e match {
      case ex: Exception if depth > 0 && Option(ex.getCause).isDefined => errorHandler(ex.getCause, depth - 1)
      case _ => IO.pure(())
    })

  /**
    * Memoize here is used to perform the parsing/allocation once (creating the Api instance), but
    * then defer the evaluation of the option/error situation until it's used. That way, if an
    * invocation won't involve a feature, there's no error in leaving that feature unconfigured.
    */
  def memoizedAPI[Cfg, Api](cfg: Option[Cfg], f: Cfg => Api, cfgName: String, apiName: String): IO[IO[Api]] =
    Async.memoize(cfg.map(f).fold[IO[Api]](
      IO.raiseError(report(s"configure $cfgName section to use $apiName capabilities"))
    )(IO.pure))
}

package mediasort.clients

import cats.effect.{Async, ContextShift, IO}
import fs2.Stream
import mediasort.config.Config
import mediasort.errors.report
import org.http4s.client.blaze._

import scala.concurrent.ExecutionContext

case class Clients(
    omdb: IO[OMDB],
    plex: IO[Plex],
    email: IO[Email]
)

object Clients {
  def fromConfig(cfg: Config)(implicit cs: ContextShift[IO]) = for {
    // TODO: more appropriate http thread pool
    httpClient <- Stream.resource(BlazeClientBuilder[IO](ExecutionContext.global).resource)
    omdb <- Stream.eval(memoizedAPI(cfg.omdb, new OMDB(_, httpClient), "omdb", "OMDB"))
    plex <- Stream.eval(memoizedAPI(cfg.plex, new Plex(_, httpClient), "plex", "Plex"))
    email <- Stream.eval(memoizedAPI(cfg.email, new Email(_), "email", "email notification"))
  } yield Clients(omdb, plex, email)

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

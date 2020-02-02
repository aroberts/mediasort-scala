package mediasort.config

import java.nio.file.{Path, Paths}

import cats.effect.concurrent.Ref
import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.yaml.parser
import cats.syntax.either._
import cats.syntax.show._
import cats.effect.{Blocker, ContextShift, IO}
import cats.effect.Async
import fs2.Stream
import fs2.io.file
import fs2.text
import io.circe.generic.extras.Configuration
import mediasort.action.Matcher
import mediasort.classify.{Classification, Classifier, Input, MediaType}
import mediasort.config.Config._
import mediasort.io.{Email, OMDB, Plex}
import mediasort.Errors._

import scala.util.Try
import scala.util.matching.Regex


case class Config(
    unclassifiedMediaType: Option[MediaType],
    omdb: Option[OMDBConfig],
    plex: Option[PlexConfig],
    email: Option[EmailConfig],
    classifiers: List[Classifier],
    actions: List[Matcher]
) {
  val omdbAPI = apiFromConfig(omdb, new OMDB(_), "omdb", "OMDB")
  val plexAPI = apiFromConfig(plex, new Plex(_), "plex", "Plex")
  val emailAPI = apiFromConfig(email, new Email(_), "email", "email notification")
  val unclassified = unclassifiedMediaType.getOrElse(MediaType("other"))

  val omdbRef = Async.memoize(apiFromConfig(omdb, new OMDB(_), "omdb", "OMDB"))

  def apiFromConfig[Cfg, Api](cfg: Option[Cfg], f: Cfg => Api, cfgName: String, apiName: String): IO[Api] =
    cfg.map(f).fold[IO[Api]](
      IO.raiseError(report(s"configure $cfgName section to use $apiName capabilities"))
    )(IO.pure)

  def actionsFor(c: Classification) = actions.filter(m =>
    m.mediaType == c.mediaType && m.confidence.forall(_ <= c.score)
  ).flatMap(_.perform)

  def classifiersFor(i: Input) = classifiers
}

object Config {
  case class OMDBConfig(apiKey: Env[String])
  case class PlexConfig(user: Env[String], password: Env[String], address: Env[String], port: Option[Int])
  case class EmailConfig(
      from: Env[String],
      host: Env[String],
      port: Env[Int],
      user: Env[String],
      password: Env[String],
      tls: Option[Boolean]
  )

  implicit val jsonCfg = Configuration.default
    .withSnakeCaseMemberNames
    .withSnakeCaseConstructorNames

  implicit val decodeConfig: Decoder[Config] = deriveConfiguredDecoder
  implicit val decodeOMDBConf: Decoder[OMDBConfig] = deriveConfiguredDecoder
  implicit val decodePlexConf: Decoder[PlexConfig] = deriveConfiguredDecoder
  implicit val decodeEmailConf: Decoder[EmailConfig] = deriveConfiguredDecoder

  implicit val decodeRegex: Decoder[Regex] = Decoder[String].emapTry(s => Try(s.r))

  def load(path: Path)(implicit cs: ContextShift[IO]): Stream[IO, Config] =
    Stream.resource(Blocker[IO]).flatMap { blocker =>
      file.readAll[IO](path, blocker, 4096)
        // utf8 chunks
        .through(text.utf8Decode[IO])
        // singleton stream of file content string
        .reduce(_.concat(_))
        // parse yaml/json
        .map(parse)
        // halt the stream on error
        .rethrow
    }

  def parse(data: String) =
    parser.parse(data).flatMap(_.as[Config])
      .leftMap(reportPrefix("error parsing config"))
}

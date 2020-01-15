package mediasort.config

import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.yaml.parser
import cats.syntax.either._
import io.circe.generic.extras.Configuration
import mediasort.action.Matcher
import mediasort.classify.Classification
import mediasort.config.Config._
import mediasort.io.{Email, OMDB, Plex}
import mediasort.{Mediasort, paths}
import os.Path

import scala.util.Try

case class Config(
    logPath: Option[Path],
    omdb: Option[OMDBConfig],
    plex: Option[PlexConfig],
    email: Option[EmailConfig],
    actions: List[Matcher]
) {
  lazy val omdbAPI = apiFromConfig(omdb, new OMDB(_), "omdb", "OMDB")
  lazy val plexAPI = apiFromConfig(plex, new Plex(_), "plex", "Plex")
  lazy val emailAPI = apiFromConfig(email, new Email(_), "email", "email notification")

  def apiFromConfig[Cfg, Api](cfg: Option[Cfg], f: Cfg => Api, cfgName: String, apiName: String) =
    cfg.map(f).getOrElse(Mediasort.fatal("")(
      new Exception(s"configure $cfgName section to use $apiName capabilities"))
    )

  def actionsFor(c: Classification) = actions.filter(m =>
    m.mediaType == c.mediaType && m.confidence <= c.score
  ).flatMap(_.perform)
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

  implicit val decodePath: Decoder[Path] = Decoder[String].emapTry(s => Try(paths.path(s)))
  implicit val decodeConfig: Decoder[Config] = deriveConfiguredDecoder
  implicit val decodeOMDBConf: Decoder[OMDBConfig] = deriveConfiguredDecoder
  implicit val decodePlexConf: Decoder[PlexConfig] = deriveConfiguredDecoder
  implicit val decodeEmailConf: Decoder[EmailConfig] = deriveConfiguredDecoder

  def load(path: Path) = Either.catchNonFatal(os.read(path))
    .flatMap(parser.parse)
    .flatMap(_.as[Config])
}

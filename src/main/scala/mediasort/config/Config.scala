package mediasort.config

import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.yaml.parser
import cats.syntax.either._
import io.circe.generic.extras.Configuration
import mediasort.action.Matcher
import mediasort.classify.Classification
import mediasort.config.Config._
import mediasort.io.{OMDB, Plex}
import mediasort.{Mediasort, paths}
import os.Path

import scala.util.Try

case class Config(
    logPath: Option[Path],
    omdb: Option[OMDBConfig],
    plex: Option[PlexConfig],
    actions: List[Matcher]
) {
  lazy val omdbAPI = apiFromConfig(omdb, new OMDB(_), "configure omdb section to use OMDB capabilities")
  lazy val plexAPI = apiFromConfig(plex, new Plex(_), "configure plex section to use Plex capabilities")

  def apiFromConfig[Cfg, Api](cfg: Option[Cfg], f: Cfg => Api, error: String) =
    cfg.map(f).getOrElse(Mediasort.fatal("")(new Exception(error)))

  def actionsFor(c: Classification) = actions.filter(m =>
    m.mediaType == c.mediaType && m.confidence <= c.score
  ).flatMap(_.perform)
}

object Config {

  implicit val jsonCfg = Configuration.default
    .withSnakeCaseMemberNames
    .withSnakeCaseConstructorNames

  implicit val decodePath: Decoder[Path] = Decoder[String].emapTry(s => Try(paths.path(s)))
  implicit val decodeConfig: Decoder[Config] = deriveConfiguredDecoder

  case class OMDBConfig(apiKey: String)
  object OMDBConfig {
    implicit val decodeOMDBConf: Decoder[OMDBConfig] = deriveConfiguredDecoder
  }

  case class PlexConfig(token: String, address: String, port: Option[Int])
  object PlexConfig {
    implicit val decodePlexConf: Decoder[PlexConfig] = deriveConfiguredDecoder
  }

  def load(path: Path) = Either.catchNonFatal(os.read(path))
    .flatMap(parser.parse)
    .flatMap(_.as[Config])
}

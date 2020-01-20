package mediasort.config

import java.nio.file.{Path, Paths}

import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.yaml.parser
import cats.syntax.either._
import io.circe.generic.extras.Configuration
import mediasort.action.Matcher
import mediasort.classify.{Classification, Classifier}
import mediasort.config.Config._
import mediasort.io.{Email, OMDB, Plex}
import mediasort.{Mediasort, paths}

import scala.io.Source
import scala.util.Try

case class Config(
    logPath: Option[Path],
    unclassifiedMediaType: Option[String],
    omdb: Option[OMDBConfig],
    plex: Option[PlexConfig],
    email: Option[EmailConfig],
    classifiers: List[Classifier],
    actions: List[Matcher]
) {
  lazy val omdbAPI = apiFromConfig(omdb, new OMDB(_), "omdb", "OMDB")
  lazy val plexAPI = apiFromConfig(plex, new Plex(_), "plex", "Plex")
  lazy val emailAPI = apiFromConfig(email, new Email(_), "email", "email notification")
  // TODO: make into media type
  val unclassified = unclassifiedMediaType.getOrElse("other")

  def apiFromConfig[Cfg, Api](cfg: Option[Cfg], f: Cfg => Api, cfgName: String, apiName: String) =
    cfg.map(f).getOrElse(Mediasort.fatal("")(
      new Exception(s"configure $cfgName section to use $apiName capabilities"))
    )

  def actionsFor(c: Classification) = actions.filter(m =>
    m.mediaType == c.mediaType && m.confidence.forall(_ <= c.score)
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

  implicit val decodePath: Decoder[Path] = Decoder[String].map(Paths.get(_))
  implicit val decodeConfig: Decoder[Config] = deriveConfiguredDecoder
  implicit val decodeOMDBConf: Decoder[OMDBConfig] = deriveConfiguredDecoder
  implicit val decodePlexConf: Decoder[PlexConfig] = deriveConfiguredDecoder
  implicit val decodeEmailConf: Decoder[EmailConfig] = deriveConfiguredDecoder

  // TODO: should this be async?
  def load(path: Path) =
    Either.catchNonFatal(Source.fromFile(path.toAbsolutePath.toFile, "UTF-8").mkString)
      .flatMap(parser.parse)
      .flatMap(_.as[Config])
}

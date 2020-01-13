package mediasort.config

import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.yaml.parser
import cats.syntax.either._
import io.circe.generic.extras.Configuration
import mediasort.action.Matcher
import mediasort.classify.Classification
import mediasort.io.OMDB
import mediasort.paths
import os.Path

import scala.util.Try

case class Config(
    logPath: Option[Path],
    omdbApiKey: String,
    actions: List[Matcher]
) {
  lazy val omdb = new OMDB(omdbApiKey)

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

  def load(path: Path) = Either.catchNonFatal(os.read(path))
    .flatMap(parser.parse)
    .flatMap(_.as[Config])
}

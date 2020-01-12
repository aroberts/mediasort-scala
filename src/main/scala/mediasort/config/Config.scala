package mediasort.config

import io.circe._
import io.circe.generic.semiauto._
import io.circe.yaml.parser
import cats.syntax.either._
import mediasort.action.Matcher
import mediasort.classify.Classification
import mediasort.io.OMDB
import mediasort.strings
import os.Path

case class Config(
    logPath: String,
    omdbApiKey: String,
    actions: List[Matcher]
) {
  lazy val omdb = new OMDB(omdbApiKey)

  def actionsFor(c: Classification) = actions.filter(m =>
    m.mediaType == c.mediaType && m.confidence <= c.score
  ).flatMap(_.perform)
}

object Config {
  implicit val decodeConfig: Decoder[Config] = deriveDecoder

  def load(path: Path) = Either.catchNonFatal(os.read(path))
    .flatMap(parser.parse)
    .flatMap(_.as[Config])
    .leftMap(strings.errorMessage())
}

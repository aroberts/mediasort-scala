package mediasort.config

import io.circe._
import io.circe.generic.semiauto._
import io.circe.yaml.parser
import cats.syntax.either._
import mediasort.action.Matcher
import mediasort.classify.Classification
import mediasort.io.OMDB
import mediasort.strings
import org.rogach.scallop.ValueConverter

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
  implicit val convertConfig: ValueConverter[Config] =
    implicitly[ValueConverter[String]].flatMap(load)

  def load(path: String) = Either.catchNonFatal(os.read(os.Path(path)))
    .flatMap(parser.parse)
    .flatMap(_.as[Config])
    .map(Option.apply)
    .leftMap(strings.errorMessage())
}

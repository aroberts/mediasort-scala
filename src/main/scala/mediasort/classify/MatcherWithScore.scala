package mediasort.classify

import io.circe.Decoder
import mediasort.config.Config.jsonCfg
import io.circe.generic.extras.semiauto._

import scala.util.Try
import scala.util.matching.Regex

case class MatcherWithScore(pattern: Regex, score: Int, titleGroup: Option[Int]) {

}
object MatcherWithScore {
  implicit val decodeRegex: Decoder[Regex] = Decoder[String].emapTry(s => Try(s.r))
  implicit val decodeMatcherWithScore: Decoder[MatcherWithScore] = deriveConfiguredDecoder
}


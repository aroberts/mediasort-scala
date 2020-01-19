package mediasort.classify

import io.circe.Decoder
import mediasort.config.Config.jsonCfg
import io.circe.generic.extras.semiauto._

case class MatcherWithScore(pattern: String, score: Int)
object MatcherWithScore {
  implicit val decodeMatcherWithScore: Decoder[MatcherWithScore] = deriveConfiguredDecoder
}


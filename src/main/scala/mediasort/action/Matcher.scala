package mediasort.action

import io.circe.Decoder
import io.circe.generic.extras.semiauto._
import mediasort.config.Config.jsonCfg
import mediasort.classify.MediaType

case class Matcher(
    mediaType: MediaType,
    confidence: Int,
    perform: List[Action]
)

object Matcher {
  implicit val decodeTrigger: Decoder[Matcher] = deriveConfiguredDecoder
}

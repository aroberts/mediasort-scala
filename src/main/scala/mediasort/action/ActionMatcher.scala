package mediasort.action

import io.circe.Decoder
import io.circe.generic.extras.semiauto._
import mediasort.config.Config.jsonCfg
import mediasort.classify.MediaType

case class ActionMatcher(
    mediaType: MediaType,
    confidence: Option[Int],
    perform: List[Action]
)

object ActionMatcher {
  implicit val decodeTrigger: Decoder[ActionMatcher] = deriveConfiguredDecoder
}

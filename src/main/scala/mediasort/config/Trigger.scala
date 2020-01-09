package mediasort.config

import io.circe.Decoder
import io.circe.generic.semiauto._
import mediasort.classify.MediaType

case class Trigger(
    mediaType: MediaType,
    confidence: Int,
    perform: List[Action]
)

object Trigger {
  implicit val decodeTrigger: Decoder[Trigger] = deriveDecoder
}

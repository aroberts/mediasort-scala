package mediasort.config

import io.circe.Decoder
import io.circe.generic.semiauto._
import mediasort.classify.MediaType

case class Matcher(
    mediaType: MediaType,
    confidence: Int,
    perform: List[Action]
)

object Matcher {
  implicit val decodeTrigger: Decoder[Matcher] = deriveDecoder
}

package mediasort.classify

import io.circe.Decoder
import io.circe.generic.extras.semiauto._

case class MediaType(value: String) extends AnyVal
object MediaType {
  implicit val decodeMediaType: Decoder[MediaType] = deriveUnwrappedDecoder
}

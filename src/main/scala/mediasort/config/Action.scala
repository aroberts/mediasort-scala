package mediasort.config

import io.circe.Decoder
import io.circe.generic.semiauto._

sealed trait Action
object Action {
  implicit val decodeAction: Decoder[Action] = deriveDecoder

  case object Placeholder extends Action
}


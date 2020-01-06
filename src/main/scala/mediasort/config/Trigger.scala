package mediasort.config

import mediasort.classify.MediaType

case class Trigger(
    mediaType: MediaType,
    confidence: Int,
    perform: List[Action]
)

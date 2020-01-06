package mediasort.classify

import mediasort.strings._

case class Classification(
    path: String,
    mediaType: MediaType,
    score: Int,
    name: Option[String] = None
) {
  lazy val normalizedName = name.map(normalize)
  lazy val label = s"${underscore(typeName(mediaType))}($score)${name.map(" " + _).getOrElse("")}"
}

object Classification {
  def none(path: String) = Classification(path, MediaType.Other, 0)
}

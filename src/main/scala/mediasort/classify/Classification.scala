package mediasort.classify

import mediasort.strings._
import os.Path

case class Classification(
    path: Path,
    mediaType: MediaType,
    score: Int,
    name: Option[String] = None
) {
  lazy val normalizedName = name.map(normalize)
  lazy val normalizedNameOrDir = normalizedName.getOrElse(normalize(path.last))

  lazy val label = s"${underscore(typeName(mediaType))}($score)${name.map(" " + _).getOrElse("")}"
}

object Classification {
  def none(path: Path) = Classification(path, MediaType.Other, 0)
}

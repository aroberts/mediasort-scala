package mediasort.classify

import cats.Show
import cats.syntax.option._
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

  def toMultiLineString = List(
    s"Path: $path".some,
    s"Kind: $mediaType".some,
    s"Score: $score".some,
    name.map(n => s"Name: $n")
  ).flatten.mkString("\n")
}

object Classification {
  def none(path: Path) = Classification(path, MediaType.Other, 0)

  implicit val showClassification: Show[Classification] = Show.show(c => List(
    Some(c.path.toString),
    Some(typeName(c.mediaType).toLowerCase + s"(${c.score})"),
    c.name
  ).flatten.mkString(" "))

}

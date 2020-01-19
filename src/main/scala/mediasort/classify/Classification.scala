package mediasort.classify

import cats.Show
import cats.syntax.option._
import mediasort.config.Config
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

  lazy val label = s"${path.toString} ${mediaType.value}($score)${name.map(" " + _).getOrElse("")}"

  def toMultiLineString = List(
    s"Path: $path".some,
    s"Kind: $mediaType".some,
    s"Score: $score".some,
    name.map(n => s"Name: $n")
  ).flatten.mkString("\n")
}

object Classification {
  def none(path: Path)(implicit cfg: Config) = Classification(path, MediaType(cfg.unclassified), 0)

  def score(i: Int) = scala.math.max(0, scala.math.min(i, 10))

  implicit val showClassification: Show[Classification] = Show.show(_.label)
}

package mediasort.classify

import java.nio.file.Path

import cats.Show
import cats.syntax.option._
import cats.syntax.semigroup._
import mediasort.config.Config
import mediasort.strings._

case class Classification(
    path: Path,
    mediaType: MediaType,
    score: Int,
    name: Option[String] = None
) {
  lazy val normalizedName = name.map(normalize)
  lazy val normalizedNameOrDir = normalizedName.getOrElse(normalize(path.getFileName.toString))

  lazy val label = s"$path ${mediaType.value}($score)${name.map(" " + _).getOrElse("")}"

  def toMultiLineString = List(
    s"Path: $path".some,
    s"Kind: $mediaType".some,
    s"Score: $score".some,
    name.map(n => s"Name: $n")
  ).flatten.mkString("\n")
}

object Classification {
  def mergeByType(in: List[Classification]): List[Classification] =
    in.groupBy(c => (c.path, c.mediaType))
      .values
      .map(
        _.sortBy(_.score)(Ordering[Int].reverse)
          .reduce((l, r) =>
            Classification(l.path, l.mediaType, score(l.score + r.score), l.name orElse r.name)
          )
      ).toList

  def none(path: Path)(implicit cfg: Config) = Classification(path, cfg.unclassified, 0)

  def score(i: Int) = scala.math.max(0, scala.math.min(i, 10))

  implicit val showClassification: Show[Classification] = Show.show(_.label)
}

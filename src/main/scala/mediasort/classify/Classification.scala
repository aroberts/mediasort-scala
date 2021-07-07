package mediasort.classify

import java.nio.file.Path

import cats.Show
import cats.syntax.option._
import mediasort.config.Config
import mediasort.strings._

import fs2.Stream

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
  private type MergeState = Map[(Path, MediaType), Classification]
  private val MergeState = Map.empty[(Path, MediaType), Classification]


  /**
    * Consumes a stream of classifications, combining classifications that share
    * the same path and media type. Combined classifications are emitted in sorted
    * order, highest score first
    */
  def merged[F[_]](classifications: Stream[F, Classification]) =
    classifications.fold(MergeState)((state, in) => {
      val key = (in.path, in.mediaType)
      val update = state.get(key).map(old =>
        Classification(key._1, key._2, score(old.score + in.score), bestName(old, in))
      ).getOrElse(in)
      state + (key -> update)
    }).flatMap(m => Stream.emits(m.values.toList.sortBy(_.score)(Ordering[Int].reverse)))

  def bestName(l: Classification, r: Classification) =
    if (l.score > r.score) l.name orElse r.name else r.name orElse l.name

  def none(path: Path, cfg: Config) = Classification(path, cfg.unclassified, 0)

  def score(i: Int) = scala.math.max(0, scala.math.min(i, 10))

  implicit val showClassification: Show[Classification] = Show.show(_.label)
}

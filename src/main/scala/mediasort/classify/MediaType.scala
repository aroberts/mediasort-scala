package mediasort.classify

import cats.effect._
import cats.syntax.traverse._
import cats.syntax.foldable._
import cats.instances.list._
import cats.instances.lazyList._
import io.circe.Decoder
import io.circe.generic.extras.semiauto._
import mediasort.classify.MimeType.MimedPath
import mediasort.config.Config
import mediasort.config.Config.jsonCfg
import mediasort.io.IMDB
import mediasort.strings
import os._

import scala.util.matching.Regex


sealed trait MediaType {
  def detect(in: Path, mimedPaths: IndexedSeq[MimedPath])(implicit cfg: Config): IO[Iterable[Classification]]
}

object MediaType {
  implicit val decodeMediaType: Decoder[MediaType] = deriveEnumerationDecoder

  case class MatcherWithScore(re: Regex, score: Int)

  case object TV extends MediaType {
    val SeasonNameRegex = MatcherWithScore(raw"(?i)(.*)(S\d\d?E\d\d?)".r, 6)
    val NightlyRegex = MatcherWithScore(raw"(.*)(\d{4}.\d{2}.\d{2})".r, 4)
    val WholeSeasonRegex = MatcherWithScore(raw"(.*)(Season.?\d\d?|S\d\d)".r, 4)

    override def detect(in: Path, mimedPaths: IndexedSeq[MimedPath])(implicit cfg: Config) =
      IO.pure {
        for {
          mws <- LazyList(SeasonNameRegex, NightlyRegex, WholeSeasonRegex)
          matched <- mws.re.findFirstMatchIn(in.last)
        } yield Classification(in, this, mws.score, name = Some(matched.group(1)))
      }.map(_.headOption)
  }

  case object Movie extends MediaType {
    val TitleAndYearRegex = MatcherWithScore(raw"^(.*)[.-_ ](\d{4})".r, 6)
    val HDYearResRegex = MatcherWithScore(raw"^(?i)(.*)[.-_ ](\d{4})[.-_ ]((?:480|720|1080)(?:p|i))".r, 8)

    private def omdbQuery(in: Path, title: String, year: String, score: Int)(implicit cfg: Config): IO[Option[Classification]] =
      cfg.omdbAPI.query(title = Some(title), year = Some(year)).map(
        _.filter(_.mediaType == this).map(_ => Classification(in, this, score, Some(title)))
      )

    def detect(in: Path, mimedPaths: IndexedSeq[MimedPath])(implicit cfg: Config) = {
      val name = in.last
      if (TV.SeasonNameRegex.re.findFirstMatchIn(name).isEmpty) {
        LazyList(HDYearResRegex, TitleAndYearRegex)
          .flatMap(mws => mws.re.findFirstMatchIn(name).map(_ -> mws.score))
          .collectFirstSomeM { case (m, score) =>
            omdbQuery(in, strings.normalize(m.group(1)), m.group(2), score)
          }.map(_.toIterable)
      } else IO.pure(None)
    }
  }

  case object Music extends MediaType {
    def detect(in: Path, mimedPaths: IndexedSeq[MimedPath])(implicit cfg: Config) =
      IO.pure(detectAudio(in, mimedPaths))
  }

  case object LosslessMusic extends MediaType {
    override def detect(in: Path, mimedPaths: IndexedSeq[MimedPath])(implicit cfg: Config) =
      IO.pure(detectAudio(in, mimedPaths))
  }

  case object Other extends MediaType {
    override def detect(in: Path, mimedPaths: IndexedSeq[MimedPath])(implicit cfg: Config) =
      IO.pure(None)
  }

  def detectAudio(in: Path, mimedPaths: IndexedSeq[MimedPath]) = {
    val mimeTypes = mimedPaths.map(_.mimeType)

    val musicTypes = mimeTypes.filter(_.contains("audio"))
    val score = Math.round(musicTypes.length * 10.0 / mimeTypes.length).toInt
    val kind: MediaType = if (musicTypes.exists(_.contains("flac"))) LosslessMusic else Music

    Some(score).filter(_ > 0).map(Classification(in, kind, _, Some(in.last)))
  }

  def fromNFOs(in: Path, mimedPaths: IndexedSeq[MimedPath])(implicit cfg: Config) = {
    mimedPaths.map(_.path)
      .filter(_.ext == "nfo")
      .toList
      .traverse(IMDB.extractFirstIMDBId)
      .map(_.flatMap(_.map(r => Classification(in, r.mediaType, 10, Some(r.title)))))
  }

}

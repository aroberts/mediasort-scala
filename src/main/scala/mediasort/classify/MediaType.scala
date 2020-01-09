package mediasort.classify

import cats.effect._
import mediasort.io.OMDB
import mediasort.strings
import os._

import scala.util.matching.Regex


sealed trait MediaType {
  def detect(in: Path): Option[IO[Classification]]
}

object MediaType {
  case class MatcherWithScore(re: Regex, score: Int)

  val omdb: OMDB = ???

  case object TV extends MediaType {
    val SeasonNameRegex = MatcherWithScore(raw"(?i)(.*)(S\d\d?E\d\d?)".r, 6)
    val NightlyRegex = MatcherWithScore(raw"(.*)(\d{4}.\d{2}.\d{2})".r, 4)
    val WholeSeasonRegex = MatcherWithScore(raw"(.*)(Season.?\d\d?|S\d\d)".r, 4)

    override def detect(in: Path) =
      (for {
        mws <- LazyList(SeasonNameRegex, NightlyRegex, WholeSeasonRegex)
        matched <- mws.re.findFirstMatchIn(in.last)
      } yield Classification(in, this, mws.score, name = Some(matched.group(1))))
        .headOption
        .map(IO.pure)
  }

  case object Movie extends MediaType {
    val TitleAndYearRegex = MatcherWithScore(raw"^(.*)[.-_ ](\d{4})".r, 6)
    val HDYearResRegex = MatcherWithScore(raw"^(?i)(.*)[.-_ ](\d{4})[.-_ ]((?:480|720|1080)(?:p|i))".r, 8)

    def detect(in: Path) = {
      val name = in.last
      // skip tv shows that may false-positive
      if (TV.SeasonNameRegex.re.findFirstMatchIn(name).isEmpty) {
        val x = for {
          mws <- LazyList(HDYearResRegex, TitleAndYearRegex)
          matched <- mws.re.findFirstMatchIn(name).map((_, mws.score))
          m = matched._1
          title = strings.normalize(m.group(1))
          year = m.group(2)
        } yield for {
          resp <- omdb.query(title = Some(title), year = Some(year))
        } yield if (resp.mediaType == this)
          // only classify as movie if omdb says so
          Classification(in, this, mws.score, Some(title))
        else Classification.none(in)

        x.headOption
      } else None
    }
  }

  def detectAudio(in: Path) = {
    val mimeTypes = os.walk(in)
      .filter(os.isFile)
      .map(MimeType.apply)

    val musicTypes = mimeTypes.filter(_.contains("audio"))
    val score = Math.round(musicTypes.length * 10.0 / mimeTypes.length).toInt

    if (score > 0) Some(IO.pure(Classification(
      in,
      if (musicTypes.exists(_.contains("flac"))) LosslessMusic else Music,
      score,
      Some(in.last)
    ))) else None
  }
  case object Music extends MediaType {
    def detect(in: Path) = detectAudio(in)
  }
  case object LosslessMusic extends MediaType {
    def detect(in: Path) = detectAudio(in)
  }
  case object Other extends MediaType {
    override def detect(in: Path) = Some(IO.pure(Classification.none(in)))
  }
}

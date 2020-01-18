package mediasort.classify

import mediasort.io.OMDB

import scala.util.matching.Regex.Match
import cats.syntax.either._

case class OMDBQueryFromGroups(
    imdbId: Option[Int],
    title: Option[Int],
    year: Option[Int],
    responseTypes: List[String]
) {

  def extract(m: Match, group: Option[Int]): Either[String, Option[String]] =
    Either.catchNonFatal(group.map(m.group)).leftMap(_ => s"Match did not contain group $group")

  def toQuery(m: Match): Either[String, OMDB.Query] = for {
      imdbVal <- extract(m, imdbId)
      titleVal <- extract(m, title)
      yearVal <- extract(m, year)
  } yield OMDB.Query(imdbVal, titleVal, yearVal)
}

package mediasort.classify

import mediasort.clients.OMDB

import scala.util.matching.Regex.Match
import cats.syntax.traverse._
import cats.instances.option._
import cats.instances.either._
import io.circe.Decoder
import io.circe.generic.extras.semiauto._
import mediasort.config.Config.jsonCfg

import mediasort.ops._

case class OMDBQueryFromGroups(
    imdbId: Option[Int],
    title: Option[Int],
    year: Option[Int],
    responseTypes: List[String]
) {
  def toQuery(m: Match) = for {
    imdbVal <- imdbId.traverse(m.safeGroup("error extracting imdb_id:"))
    titleVal <- title.traverse(m.safeGroup("error extracting title:"))
    yearVal <- year.traverse(m.safeGroup("error extracting year:"))
  } yield OMDB.Query(imdbVal, titleVal, yearVal)
}

object OMDBQueryFromGroups {
  implicit val decodeOMDBQueryFromGroups: Decoder[OMDBQueryFromGroups] = deriveConfiguredDecoder
}

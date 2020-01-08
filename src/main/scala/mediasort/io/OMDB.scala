package mediasort.io

import sttp.client._
import sttp.client.circe._
import io.circe._
import io.circe.generic.semiauto._

import mediasort.classify.MediaType

class OMDB(apiKey: String) {

  def query(
      title: Option[String] = None,
      imdbId: Option[String] = None,
      year: Option[String] = None
  ) = basicRequest
      .get(uri"http://www.omdbapi.com/?apikey=$apiKey&i=$imdbId&t=$title&year=$year")
      .response(asJson[OMDB.Response])
      .send()
}

object OMDB {
  case class Response(Response: String, Type: String) {
    lazy val mediaType = Type match {
      case "movie" => MediaType.Movie
      case "episode" | "series" => MediaType.TV
      case _ => MediaType.Other
    }
  }
  object Response {
    implicit val jsonDecodeResponse: Decoder[Response] = deriveDecoder
  }
}

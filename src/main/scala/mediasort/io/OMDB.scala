package mediasort.io

import cats.effect.IO
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
  ) = {
    val url = uri"http://www.omdbapi.com/?apikey=$apiKey&i=$imdbId&t=$title&year=$year"
    // TODO: log request or params
    basicRequest
      .get(url)
      .response(asJson[OMDB.Response])
      .send()
      .flatMap(handleJsonResponse)
  }


  def handleJsonResponse(response: Response[Either[ResponseError[Error], OMDB.Response]]) = {
    // TODO: debug logging
    // TODO: error logging
    response.body.fold(IO.raiseError, IO.pure)
  }
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

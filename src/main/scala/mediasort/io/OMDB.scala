package mediasort.io

import cats.effect.IO
import sttp.client._
import sttp.client.circe._
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
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

  // TODO: collapse this to above
  def handleJsonResponse(response: Response[Either[ResponseError[Error], OMDB.Response]]) = {
    // TODO: debug logging
    // TODO: error logging
    // TODO: we probably don't want to raise error here, we probably instead want to log any errors
    // and convert this to Option[Success]
    response.body.toOption.flatMap(_.failure).foreach(logError)
    response.body.fold(IO.raiseError, r => IO.pure(r.success))
  }

  def logError(in: OMDB.Response.Failure) = () // stub
}

object OMDB {
  sealed trait Response {
    def success: Option[Response.Success]
    def failure: Option[Response.Failure]
  }
  object Response {
    case class Failure(error: String) extends Response {
      override def success = None
      override def failure = Some(this)
    }
    case class Success(`type`: String, title: String) extends Response {
      override def success = Some(this)
      override def failure = None

      lazy val mediaType: MediaType = `type` match {
        case "movie" => MediaType.Movie
        case "episode" | "series" => MediaType.TV
        case _ => MediaType.Other
      }
    }

    private implicit val omdbCfg: Configuration = Configuration.default.copy(
      transformMemberNames = s => s"${s.head.toUpper}${s.tail}",
      transformConstructorNames = {
        case "True" => "Success"
        case "False" => "Failure"
        case s => s
      },
      discriminator = Some("Response")
    )

    implicit val jsonDecodeResponse: Decoder[Response] = deriveConfiguredDecoder
  }

}

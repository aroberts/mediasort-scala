package mediasort.io

import cats.effect.IO
import cats.syntax.either._
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
    basicRequest
      .get(url)
      .response(asJson[OMDB.Response])
      .send()
      .flatMap(_.body.fold(
        IO.raiseError,
        r => IO.pure(
          r.toEither
            .leftMap(e => scribe.error(s"[OMDB] ${e.error} $url"))
            .toOption
        )
      ))
  }
}

object OMDB {
  sealed trait Response {
    def toEither: Either[Response.Failure, Response.Success]
  }
  object Response {
    case class Failure(error: String) extends Response {
      override def toEither = Left(this)
    }
    case class Success(`type`: String, title: String) extends Response {
      override def toEither = Right(this)

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

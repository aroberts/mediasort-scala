package mediasort.io

import cats.effect.IO
import cats.syntax.either._
import sttp.client._
import sttp.client.circe._
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import mediasort.config.Config.OMDBConfig
import mediasort.io.OMDB.Query

class OMDB(cfg: OMDBConfig) {


  def query(q: Query) = {
    val url = uri"http://www.omdbapi.com/?${q.toParams(cfg.apiKey.value)}"
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
  case class Query(
      title: Option[String] = None,
      imdbId: Option[String] = None,
      year: Option[String] = None
  ) {
    def toParams(apiKey: String) = Map(
      "apikey" -> apiKey,
      "i" -> imdbId,
      "t" -> title,
      "year" -> year,
    )
  }

  sealed trait Response {
    def toEither: Either[Response.Failure, Response.Success]
  }
  object Response {
    case class Failure(error: String) extends Response {
      override def toEither = Left(this)
    }
    case class Success(`type`: String, title: String) extends Response {
      override def toEither = Right(this)

      def containsAnyType(types: List[String]) = types.contains(`type`)
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

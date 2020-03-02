package mediasort.clients

import cats.effect.IO
import cats.syntax.either._
import sttp.client._
import sttp.client.circe._
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import mediasort.classify.MediaType
import mediasort.config.Config.{MediaTypeMapping, OMDBConfig}
import mediasort.clients.OMDB.Query

class OMDB(cfg: OMDBConfig)(implicit backend: SttpBackend[IO, Nothing, Nothing]) {

  lazy val typeMapping = cfg.typeMapping.fold(List.empty[MediaTypeMapping])(_.toList)

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

  def mediaType(r: OMDB.Response.Success) = r.toMediaType(typeMapping)
}

object OMDB {
  def apply(cfg: OMDBConfig, backend: SttpBackend[IO, Nothing, Nothing]) = new OMDB(cfg)(backend)

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
      def toMediaType(mapping: List[MediaTypeMapping]) =
        mapping.find(_.input.matches(`type`)).fold(MediaType(`type`))(_.output)
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

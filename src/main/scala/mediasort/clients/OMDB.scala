package mediasort.clients

import cats.effect.IO
import cats.syntax.either._
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import mediasort.classify.MediaType
import mediasort.config.Config.{MediaTypeMapping, OMDBConfig}
import mediasort.clients.OMDB.Query

import org.http4s.client._
import org.http4s.syntax.all._
import org.http4s.circe.CirceEntityDecoder._

class OMDB(cfg: OMDBConfig, client: Client[IO]) {

  lazy val typeMapping = cfg.typeMapping.fold(List.empty[MediaTypeMapping])(_.toList)

  def query(q: Query) = {
    val uri = uri"http://www.omdbapi.com/".withQueryParams(q.toParams(cfg.apiKey.value))
    client.expect[OMDB.Response](uri).flatMap {
      case s: OMDB.Response.Success => IO.pure(Some(s))
      case OMDB.Response.Failure(error) =>
        IO(scribe.debug(s"[OMDB] $error $uri")).as(None)
    }
  }

  def mediaType(r: OMDB.Response.Success) = r.toMediaType(typeMapping)
}

object OMDB {
  case class Query(
      title: Option[String] = None,
      imdbId: Option[String] = None,
      year: Option[String] = None
  ) {
    def toParams(apiKey: String) = List(
      title.map("t" -> _),
      imdbId.map("i" -> _),
      year.map("year" -> _),
      Some("apikey" -> apiKey)
    ).flatten.toMap
  }

  sealed trait Response
  object Response {
    case class Failure(error: String) extends Response
    case class Success(`type`: String, title: String) extends Response {
      def toMediaType(mapping: List[MediaTypeMapping]) =
        mapping.find(_.input.matches(`type`)).fold(MediaType(`type`))(_.output)
    }

    private implicit val omdbCfg: Configuration = Configuration.default.copy(
      transformMemberNames = s => s"${s.head.toUpper}${s.tail}",
      transformConstructorNames = {
        case "Success" => "True"
        case "Failure" => "False"
        case s => s
      },
      discriminator = Some("Response")
    )

    implicit val jsonDecodeResponse: Decoder[Response] = deriveConfiguredDecoder
  }

}

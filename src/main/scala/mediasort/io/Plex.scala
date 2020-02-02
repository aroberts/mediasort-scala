package mediasort.io

import cats.effect.IO
import cats.syntax.either._
import mediasort.strings._
import sttp.client._
import Plex._
import cats.data.EitherT
import mediasort.Mediasort
import mediasort.config.Config.PlexConfig

import scala.xml.{Elem, XML}

class Plex(cfg: PlexConfig) {
  val baseUrl = s"http://${cfg.address.value}:${cfg.port.getOrElse(32400)}"
  val clientName = "mediasort"
  val clientVersion = Mediasort.version
  val clientId = java.security.MessageDigest.getInstance("SHA-1")
    .digest(s"$clientName v$clientVersion".getBytes("UTF-8"))
    .map("%02x".format(_))
    .mkString

  lazy val token = {
    val res = basicRequest
      .post(uri"https://plex.tv/users/sign_in.xml")
      .body("user[login]" -> cfg.user.value, "user[password]" -> cfg.password.value)
      .headers(Map(
        "X-Plex-Client-Identifier" -> clientId,
        "X-Plex-Product" -> clientName,
        "X-Plex-Version" -> clientVersion
      ))
      .response(asXML)
      .send()

    EitherT(res.map(_.body))
      .map(_ \\ "authentication-token")
      .subflatMap(nodes => Either.fromOption(nodes.headOption, err("No token in response received from Plex")))
      .map(_.text)
  }

  def getRequest[A](paths: Any*)(as: ResponseAs[Either[Throwable, A], Nothing])(
      params: Map[String, Any] = Map(),
      headers: Map[String, String] = Map(),
  ) = {
    val url = uri"$baseUrl/$paths?$params"
    scribe.debug(s"[PLEX] GET ${url.toString}")

    token.flatMapF(t => basicRequest
      .get(uri"$baseUrl/$paths?$params")
      .response(as)
      .headers(headers)
      .header("X-Plex-Token", t)
      .send()
      .map(r => {
        scribe.debug(s"[PLEX] ${r.code}")
        scribe.trace(s"[PLEX] ${r.body.fold(_.toString, _.toString)}")
        r.body
      })
    )
  }

  def listSections = getRequest("library","sections")(asXML)()
  def refreshSectionById(sectionId: Int, force: Boolean = false) = {
    getRequest(s"library", "sections", sectionId, "refresh")(asEmpty)(
      params = Map("force" -> (if (force) Some(1) else None))
    )
  }

  private def pureEitherT[A, B](a: Option[A], other: => B) = EitherT(IO.pure(Either.fromOption(a, other)))
  private def pureEitherT[A](a: => A) = EitherT(IO.pure(Either.catchNonFatal(a)))

  def refreshSection(sectionName: String, force: Boolean = false) = {
    val res = for {
      sections <- listSections
      sectionElem = sections \\ "Directory" find(_.attribute("title").exists(_.text == sectionName))
      section <- pureEitherT(sectionElem, err(s"Section title '$sectionName' not found"))
      id <- pureEitherT(section.attribute("key").flatMap(_.headOption).map(_.text), err(s"No id for section '$sectionName'"))
      idInt <- pureEitherT(id.toInt)
      refresh <- refreshSectionById(idInt, force)
    } yield refresh

    // or fail on errors?
    res.leftMap(e => scribe.error(s"[PLEX] ${errorMessage(e)}"))
      .value.map(_ => ()) // discard output
  }

}

object Plex {
  def err(s: String) = new Exception(s)

  def asEmpty: ResponseAs[Either[Throwable, Unit], Nothing] =
    asString.map(ResponseAs.deserializeRightWithError({
      case "" => Right(())
      case other => Left(err(s"Expected empty response, but got '$other'"))
    }))

  def asXML: ResponseAs[Either[Throwable, Elem], Nothing] =
    asString.map(ResponseAs.deserializeRightWithError(deserializeXML))

    private def deserializeXML(s: String) = Either.catchNonFatal(XML.loadString(s))
}

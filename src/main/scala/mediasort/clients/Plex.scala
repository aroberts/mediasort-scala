package mediasort.clients

import cats.effect.IO
import cats.syntax.either._
import cats.syntax.show._
import mediasort.errors._
import mediasort.Mediasort
import mediasort.config.Config.PlexConfig

import org.http4s._
import org.http4s.client._
import org.http4s.syntax.all._
import org.http4s.scalaxml._
import org.http4s.client.dsl.io._

class Plex(cfg: PlexConfig, client: Client[IO]) {
  // response type alias because wtf is "Elem"
  private type XML = scala.xml.Elem

  val host = cfg.serverAddress.value
  val hostAndScheme = if (host.contains("://")) host else s"http://$host"
  val baseUrl = IO.fromEither(Uri.fromString(s"$hostAndScheme:${cfg.port.getOrElse(32400)}"))

  val clientName = "mediasort"
  val clientVersion = Mediasort.version
  val clientId = java.security.MessageDigest.getInstance("SHA-1")
    .digest(s"$clientName v$clientVersion".getBytes("UTF-8"))
    .map("%02x".format(_))
    .mkString


  val token = client.expect[XML](Method.POST(
    UrlForm("user[login]" -> cfg.user.value, "user[password]" -> cfg.password.value),
    uri"https://plex.tv/users/sign_in.xml",
    Header("X-Plex-Client-Identifier", clientId),
    Header("X-Plex-Product", clientName),
    Header("X-Plex-Version", clientVersion)
  )).map(n => Either.fromOption(
    (n \\ "authentication-token").headOption.map(_.text),
    err("No token in response received from Plex"))
  ).flatMap(IO.fromEither)

  def getRequest[ResponseType](
      path: String,
      params: Uri => Uri = identity,
      headers: Map[String, String] = Map(),
  )(implicit d: EntityDecoder[IO, ResponseType]) = for {
    base <- baseUrl
    url = params(base.addPath(path))
    tk <- token
    _ <- IO(scribe.debug(s"[PLEX] GET ${url.toString}"))
    authedHeaders = (headers + ("X-Plex-Token" -> tk)).toList.map(h => Header(h._1, h._2))
    res <- client.expect[ResponseType](Method.GET(url, authedHeaders: _*))
  } yield res

  def listSections = getRequest[XML]("library/sections")
  def refreshSectionById(sectionId: Int, force: Boolean = false) = getRequest[Unit](
    s"library/sections/$sectionId/refresh",
    u => if (force) u.withQueryParam("force", 1) else u
  )

  private def IOFromOption[A, B](a: Option[A], other: => Throwable) = IO.fromEither(Either.fromOption(a, other))
  private def IOFromNonFatal[A](a: => A) = IO.fromEither(Either.catchNonFatal(a))
  private def err(s: String) = new Exception(s)

  def refreshSection(sectionName: String, force: Boolean = false) = {
    val res = for {
      sections <- listSections
      sectionElem = sections \\ "Directory" find(_.attribute("title").exists(_.text == sectionName))
      section <- IOFromOption(sectionElem, err(s"Section title '$sectionName' not found"))
      id <- IOFromOption(section.attribute("key").flatMap(_.headOption).map(_.text), err(s"No id for section '$sectionName'"))
      idInt <- IOFromNonFatal(id.toInt)
      refresh <- refreshSectionById(idInt, force)
    } yield refresh


    // or fail on errors?
    res.as(())
      .handleErrorWith(e => IO(scribe.error(s"[PLEX] ${e.show}")))
  }

}

object Plex {
}

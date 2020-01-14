package mediasort.io

import cats.effect.IO
import cats.syntax.either._
import mediasort.strings._
import sttp.client._
import Plex._
import cats.data.EitherT

import scala.xml.{Elem, XML}

class Plex(serverAddress: String, port: Int, token: String) {
  val baseUrl = s"http://$serverAddress:$port"
//    .leftMap(e => scribe.error(s"[PLEX] ${errorMessage(e)} $url"))

  def listSections = {
    val url = uri"$baseUrl/library/sections?X-Plex-Token=$token"
    basicRequest
      .get(url)
      .response(asXML)
      .send()
  }

  def refreshSectionById(sectionId: Int, force: Boolean = false) = {
    val forceParam = if (force) Some(1) else None
    val url = uri"$baseUrl/library/sections/$sectionId/refresh?force=$forceParam&X-Plex-Token=$token"
    basicRequest
      .get(url)
      .response(asXML)
      .send()
  }

  private def err(s: String) = new Exception(s)
  private def pureEitherT[A, B](a: Option[A], other: => B) = EitherT(IO.pure(Either.fromOption(a, other)))
  private def pureEitherT[A](a: => A) = EitherT(IO.pure(Either.catchNonFatal(a)))

  def refreshSection(sectionName: String, force: Boolean = false) = (for {
    sections <- EitherT(listSections.map(_.body))
    sectionElem = sections \\ "Directory" find(_.attribute("title").exists(_.text == sectionName))
    section <- pureEitherT(sectionElem, err(s"Section title '$sectionName' not found"))
    id <- pureEitherT(section.attribute("key").flatMap(_.headOption).map(_.text), err(s"No id for section '$sectionName'"))
    idInt <- pureEitherT(id.toInt)
    refresh <- EitherT(refreshSectionById(idInt, force).map(_.body))
  } yield refresh).value

}

object Plex {
  def asXML: ResponseAs[Either[Throwable, Elem], Nothing] =
    asString.map(ResponseAs.deserializeRightWithError(deserializeXML))

    private def deserializeXML(s: String) = Either.catchNonFatal(XML.loadString(s))
}

package mediasort

import cats.Show
// TODO: remove io package object and fix this
import _root_.io.circe.{DecodingFailure, ParsingFailure}
import cats.syntax.show._
import sttp.client.{DeserializationError, HttpError}

object Errors {
  case class ReportingError(msg: String, cause: Option[Throwable]) extends Exception(msg, cause.orNull) {
    val report: String = List(
      Some(msg),
      cause.flatMap {
        case e: ReportingError => Some(e.report)
        case _ => None
      }
    ).flatten.mkString(": ")
  }

  def report(s: String): ReportingError = ReportingError(s, None)
  def report(t: Throwable): ReportingError = ReportingError(t.getMessage, Some(t))
  def reportPrefix(prefix: String)(t: Throwable): ReportingError = ReportingError(prefix, Some(report(t)))

  implicit val showError: Show[Throwable] = Show.show {
    case e: ReportingError => e.report
    case e: ParsingFailure => e.show
    case e: DecodingFailure => e.show
    case e: HttpError => e.body
    case e: DeserializationError[_] => e.original
    case e: Exception => e.getMessage
    case t => t.toString
  }
}
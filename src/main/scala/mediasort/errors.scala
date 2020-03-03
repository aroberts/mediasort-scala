package mediasort

import cats.Show
import io.circe.{DecodingFailure, ParsingFailure}
import cats.syntax.show._

object errors {
  case class ReportingError(msg: String, cause: Option[Throwable]) extends Exception(msg, cause.orNull)

  def report(s: String): ReportingError = ReportingError(s, None)
  def reportPrefix[A <: Throwable: Show](prefix: String)(t: A): ReportingError =
    ReportingError(s"$prefix: ${t.show}", Some(t))

  implicit val showError: Show[Throwable] = Show.show {
    case e: ReportingError => e.msg
    case e: ParsingFailure => e.show
    case e: DecodingFailure => e.show
    case e: Exception => e.getMessage
    case t => t.toString
  }
}

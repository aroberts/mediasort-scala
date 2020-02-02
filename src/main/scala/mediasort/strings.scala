package mediasort

import cats.implicits._
import _root_.io.circe.{DecodingFailure, ParsingFailure}
import sttp.client.{DeserializationError, HttpError}

import scala.annotation.tailrec

object strings {
  // from https://gist.github.com/sidharthkuruvila/3154845#gistcomment-2622928
  def underscore(in: String) = {
    @tailrec
    def go(accDone: List[Char], acc: List[Char]): List[Char] = acc match {
      case Nil => accDone
      case a :: b :: c :: tail if a.isUpper && b.isUpper && c.isLower => go(accDone ++ List(a, '_', b, c), tail)
      case a :: b :: tail if a.isLower && b.isUpper => go(accDone ++ List(a, '_', b), tail)
      case a :: tail => go(accDone :+ a, tail)
    }
    go(Nil, in.toList).mkString.toLowerCase
  }

  val spacers = "[ -._]".r
  def normalize(in: String) = spacers.replaceAllIn(in, " ").trim

  def typeName[A](a: A) = a.getClass.getSimpleName.stripSuffix("$")

  def errorMessage(t: Throwable): String = t match {
    case e: ParsingFailure => e.show
    case e: DecodingFailure => e.show
    case e: HttpError => e.body
    case e: DeserializationError[_] => e.original
    case e: Exception => e.getMessage
    case _ => t.toString
  }

}

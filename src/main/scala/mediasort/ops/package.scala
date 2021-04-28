package mediasort

import cats.data.NonEmptyList

import scala.util.matching.Regex
import cats.syntax.either._
import fs2.{Chunk, Pull, Stream}

import errors._

package object ops {
  implicit class RegexMatchOps(m: Regex.Match) {
    private def rawSafeGroup(i: Int) = Either.catchNonFatal(m.group(i))
    def safeGroup(prefix: String)(i: Int) =
      rawSafeGroup(i).leftMap(errors.reportPrefix(prefix))
  }


  implicit class StreamOps[F[_], O](stream: Stream[F, O]) {
    def splitInclusive(f: O => Boolean): Stream[F, Chunk[O]] = {
      def go(buffer: List[Chunk[O]], splitElem: Chunk[O], s: Stream[F, O]): Pull[F, Chunk[O], Unit] =
        s.pull.uncons.flatMap {
          case Some((hd, tl)) =>
            hd.indexWhere(f) match {
              case None => go(hd :: buffer, splitElem, tl)
              case Some(idx) =>
                val pfx = hd.take(idx)
                val b2 = pfx :: buffer
                Pull.output1(Chunk.concat(splitElem :: b2.reverse)) >> go(Nil, hd.drop(idx).take(1), tl.cons(hd.drop(idx + 1)))
            }
          case None =>
            if (buffer.nonEmpty || splitElem.nonEmpty) Pull.output1(Chunk.concat(splitElem :: buffer.reverse))
            else Pull.done
        }
      go(Nil, Chunk.empty[O], stream).stream
    }

    def foldFailFast[O2](failWhen: O => Boolean, acc: O2, f: (O2, O) => O2): Stream[F, O2] = {
      def go(s: Stream[F, O], failWhen: O => Boolean, acc: O2, f: (O2, O) => O2): Pull[F, O2, Unit] = {
        s.pull.uncons1.flatMap {
          case None => Pull.output1(acc) >> Pull.done
          case Some((hd, tl)) =>
            val updatedAcc = f(acc, hd)
            if (failWhen(hd)) Pull.output1(updatedAcc) >> Pull.done
            else go(tl, failWhen, updatedAcc, f)
        }
      }

      go(stream, failWhen, acc, f).stream
    }

  }

  implicit class NelOps[A](nel: NonEmptyList[A]) {
    def contains(a: A) = nel.head == a || nel.tail.contains(a)
  }
}

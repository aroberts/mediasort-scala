package mediasort.ops

import cats.effect.{Async, IO}
import mediasort.Spec

class MemoizeSpec extends Spec {

  case class MemTest(i: Int) {
    val range = Async.memoize(IO {
      println("computing range")
      Range(0,i).toList
    })

    val squared = Async.memoize(for {
     io <- range
     list <- io
    } yield list.map(k => k * k))
  }


  it should "run once" in {
    val input = MemTest(5)
    val p = for {
      io1 <- input.range
      range1 <- io1
      range2 <- io1
      io2 <- input.squared
      squared1 <- io2
      squared2 <- io2
    } yield ()

    p.unsafeRunSync
  }

}

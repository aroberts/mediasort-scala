package mediasort.classify

import mediasort.Spec
import cats.effect._
import cats.syntax.foldable._
import cats.instances.all._

class MediaTypeSpec extends Spec {

  it should "use io properly with lazy list" in {
    var callCount = 0
    def f(i: Int) = IO {
      callCount += 1
      if (i % 2 == 0) Some(i) else None
    }

    val io = LazyList(1,2,3,4,5).collectFirstSomeM(f)
    val res = io.unsafeRunSync
    assert(res.contains(2))
    assert(callCount == 2)
  }

}

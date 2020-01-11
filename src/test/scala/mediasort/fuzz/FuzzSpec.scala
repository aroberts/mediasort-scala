package mediasort.fuzz

import mediasort.Test

class FuzzSpec extends Test {

  def compareDouble(lhs: Double, rhs: Double, len: Int) = {
    val pow = scala.math.pow(10, len)
    assert((lhs * pow).toLong == (rhs * pow).toLong)
  }

  def assertRatio[A](lhs: Iterable[A], rhs: Iterable[A], ev: Double) =
    compareDouble(ratio(lhs, rhs), ev, 3)

  it should "calculate ratio of 0 for unrelated strings" in assertRatio("asdf", "zxcv", 0)
  it should "calculate ratio for insertion" in assertRatio("ab", "a", .666)
  it should "calculate ratio per example" in assertRatio("abcd", "dcba", .250)

}

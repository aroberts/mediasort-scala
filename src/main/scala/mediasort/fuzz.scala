package mediasort

import scala.math.min

package object fuzz {

  // levenshtein with replacement cost of 2, insertion/deletion cost of 1
  def levenshtein[A](lhs: Iterable[A], rhs: Iterable[A]): Int =
    lhs.foldLeft((0 to rhs.size).toList)((prev, a) =>
      prev.zip(prev.tail).zip(rhs).scanLeft(prev.head + 1) {
        case (l, ((d, u), b)) => min(min(l + 1, u + 1), d + (if (a == b) 0 else 2))
      }).last

  def ratio[A](lhs: Iterable[A], rhs: Iterable[A]): Double =
    1 - (levenshtein(lhs, rhs).toDouble / (lhs.size + rhs.size))
}

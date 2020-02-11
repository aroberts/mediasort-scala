package mediasort.classify

import mediasort.Spec
import cats.data.NonEmptyList

class FilterSetSpec extends Spec {
  behavior of "describes"

  val baseFiles = List("1.md", "2.md", "3.txt")
  val baseFilters = Some(NonEmptyList.of( ".md", ".txt"))

  it should "with only" in {
    val f = FilterSet[String](only = baseFilters)

    assert(f.describes(baseFiles)(_.endsWith))
    assert(!f.describes(baseFiles)(_.startsWith))
    assert(!f.describes(baseFiles :+ "four")(_.contains))
  }

  it should "with exclude" in {
    val f = FilterSet[String](exclude = baseFilters)

    assert(f.describes(List("new stuff", "no dot md or txt here"))(_.contains))
    assert(!f.describes(baseFiles)(_.contains))
  }

  it should "with any" in {
    val f = FilterSet[String](any = baseFilters)
    val other = List("new stuff", "no dot md here.txt")
    assert(f.describes(baseFiles ++ other)(_.contains))
    assert(!f.describes(other)(_.contains))
  }

}

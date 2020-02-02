package mediasort

import cats.data.NonEmptyList

import java.nio.file.{Path, Paths}

class ExtensionFilterSpec extends Spec {

  val input = List(
    "one.txt",
    "two.xls",
    "three.md"
  ).map(Paths.get(_))

  def assertFilter(only: List[String], exclude: List[String], expected: List[Path]) =
    assert(
      input.filter(
        paths.extensionFilter(NonEmptyList.fromList(only), NonEmptyList.fromList(exclude))
      ) == expected
    )

  it should "do nothing with no filters" in
    assertFilter(List.empty, List.empty, input)

  it should "filter with only" in {
    assertFilter(List("md"), List.empty, input.drop(2))
    assertFilter(List("md", "xls"), List.empty, input.drop(1))
  }

  it should "filter with exclude" in {
    assertFilter(List.empty, List("md"), input.take(2))
    assertFilter(List.empty, List("txt", "md"), input.slice(1, 2))
  }

  it should "prefer only" in {
    assertFilter(List.empty, List("md"), input.take(2))
    assertFilter(List("txt"), List("txt", "md"), input.take(1))
  }
}

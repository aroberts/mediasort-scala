package mediasort.action

import cats.data.NonEmptyList
import io.circe.yaml.parser
import mediasort.{Spec, paths}
import mediasort.action.Action._

class ActionSpec extends Spec {
  it should "include/exclude correctly" in {
    val cct = CopyContentsTo(
      paths.path("."),
      None,
      None,
      Some(NonEmptyList.of("a","b", "c")),
      None
    )

    assert(!cct.extFilter(paths.path("1.c")))
    assert(cct.extFilter(paths.path("1.d")))
  }
}

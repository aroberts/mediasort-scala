package mediasort

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import scribe.Level

class Spec extends AnyFlatSpec with Matchers {
  scribe.Logger.root.clearHandlers().clearModifiers().withHandler(minimumLevel = Some(Level.Debug)).replace()
}

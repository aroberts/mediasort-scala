package mediasort

import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers
import scribe.Level

class AsyncSpec extends AsyncFlatSpec with AsyncIOSpec with Matchers {
  scribe.Logger.root.clearHandlers().clearModifiers().withHandler(minimumLevel = Some(Level.Debug)).replace()
}

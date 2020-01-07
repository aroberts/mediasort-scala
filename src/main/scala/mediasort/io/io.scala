package mediasort

import cats.effect._
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend

import scala.concurrent.ExecutionContext

package object io {
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val sttpBackend = AsyncHttpClientCatsBackend[IO]()
}

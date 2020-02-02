package mediasort

import cats.effect._
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend

import Mediasort.cs

package object io {
  implicit val sttpBackend = AsyncHttpClientCatsBackend[IO]().unsafeRunSync()
}

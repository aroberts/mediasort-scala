package mediasort.io

import cats.effect.IO
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import mediasort.Mediasort.cs

object http {
  // for some reason declaring this in the Mediasort.scala causes the program
  // to hang on completion
  val sttpClient = AsyncHttpClientCatsBackend[IO]().unsafeRunSync()

}

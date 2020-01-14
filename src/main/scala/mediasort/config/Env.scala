package mediasort.config

import io.circe._

case class Env[A](value: A) extends AnyVal
object Env {
  def lookup(s: String): String = if (s.startsWith("$")) {
    sys.env.getOrElse(s.substring(1), s)
  } else s

  implicit def decodeEnv[A](implicit inner: Decoder[A]): Decoder[Env[A]] =
    inner.prepare(_.withFocus(_.mapString(lookup))).map(Env.apply)

}

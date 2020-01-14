package mediasort.config

import mediasort.Spec

import io.circe.parser._

class EnvSpec extends Spec {
  it should "decode envs without $ literally" in {
    decode[Env[String]](""""a"""")
      .fold(fail(_), v => assert(v.value == "a"))
  }

  it should "decode envs with $ by dereference" in {
    decode[Env[String]](""""$USER"""")
      .fold(fail(_), v => assert(v.value == sys.env("USER")))
  }
}

package mediasort.clients

import mediasort.Spec
import io.circe.parser._

class OMDBSpec extends Spec {
  val errString = "Incorrect IMDb ID."
  val errorJson = s"""{
    "Response" : "False",
    "Error" : "$errString"
  }"""

  it should "parse omdb error responses" in
    assert(decode[OMDB.Response](errorJson).contains(OMDB.Response.Failure(errString)))

}

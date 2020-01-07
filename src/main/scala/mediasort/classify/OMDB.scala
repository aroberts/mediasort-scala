package mediasort.classify

class OMDB(apiKey: String) {

  def query(in: OMDB.Query): Either[String, OMDB.Result] = ???

}

object OMDB {
  case class Result(name: String)
  case class Query(title: Option[String], imdbId: Option[String], year: Option[String])
}

package mediasort.io

import mediasort.paths
import cats.syntax.either._

object IMDB {
  val UrlRegex = "^(?i)https?://www.imdb.com/title/(tt[0-9]+)/$".r
  val IdRegex = "(tt[0-9]{7})".r

  def findNfos(dir: os.Path) =
    paths.expandFiles(dir)
      .filter(_.ext == "nfo")

  def extractFirstIMDBId(nfo: os.Path) =
    Either.catchNonFatal(os.read(nfo)).map(data =>
      LazyList(UrlRegex, IdRegex).flatMap(_.findFirstMatchIn(data)).headOption
    )


}

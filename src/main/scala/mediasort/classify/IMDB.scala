package mediasort.classify

object IMDB {
  val UrlRegex = "^(?i)https?://www.imdb.com/title/(tt[0-9]+)/$".r
  val IdRegex = "(tt[0-9]{7})".r

  def findNfos(dir: os.Path) =
    os.walk(dir)
      .filter(_.ext == "nfo")
      .filter(os.isFile)

  def extractFirstIMDBId(nfo: os.Path) = {
    val data = os.read(nfo)
    LazyList(UrlRegex, IdRegex).flatMap(_.findFirstMatchIn(data)).headOption
  }


}

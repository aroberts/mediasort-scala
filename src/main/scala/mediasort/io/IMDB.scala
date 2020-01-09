package mediasort.io

import cats.effect._
import cats.syntax.traverse._
import cats.instances.option._
import mediasort.config.Config


object IMDB {
  val UrlRegex = "^(?i)https?://www.imdb.com/title/(tt[0-9]+)/$".r
  val IdRegex = "(tt[0-9]{7})".r

  def extractFirstIMDBId(nfo: os.Path)(implicit cfg: Config) =
    IO(os.read(nfo)).map(data =>
      LazyList(UrlRegex, IdRegex).flatMap(_.findFirstMatchIn(data)).headOption
    ).flatMap(_.traverse(id => cfg.omdb.query(imdbId = Some(id.group(1)))))


}

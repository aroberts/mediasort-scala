package mediasort.classify

import io.circe.Decoder
import mediasort.config.Config.jsonCfg
import io.circe.generic.extras.semiauto._

import mediasort.ops._

import cats.instances.option._
import cats.instances.either._
import cats.syntax.traverse._

import scala.util.Try
import scala.util.matching.Regex

case class MatcherWithScore(pattern: Regex, score: Int, titleGroup: Option[Int]) {
  def title(m: Regex.Match) = titleGroup.traverse(id =>
    m.safeGroup(s"couldn't extract group $id from $pattern")(id)
  )
}
object MatcherWithScore {
  implicit val decodeRegex: Decoder[Regex] = Decoder[String].emapTry(s => Try(s.r))
  implicit val decodeMatcherWithScore: Decoder[MatcherWithScore] = deriveConfiguredDecoder
}


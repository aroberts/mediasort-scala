package mediasort.classify

import cats.Monad
import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.traverse._
import cats.instances.list._
import io.circe.Decoder
import io.circe.generic.extras.semiauto._
import mediasort.config.Config
import mediasort.config.Config.jsonCfg
import ClassifierStep._
import fs2.{Chunk, Stream}
import mediasort.{errors, strings}
import mediasort.io.OMDB

import scala.util.matching.Regex

case class Classifier(
    mediaType: MediaType,
//    containsAnyMimePatterns: Option[NonEmptyList[Regex]],
//    containsAllMimePatterns: Option[NonEmptyList[Regex]],
//    excludeAnyMimePatterns: Option[NonEmptyList[Regex]],
//    same for filename patterns,
//    same for file tree patterns?
    criteria: List[ClassifierStep]
) {
  def applies(i: Input) = true //i.mimeTypes.something - patterns
}

object Classifier {

  def classifications(i: Input, classifiers: List[Classifier], omdb: IO[OMDB]) =
    classifiers.flatTraverse(c => c.criteria.traverse {
      case s: BasicClassifierStep => s.classify(c.mediaType, i)
      case s: OMDBClassifierStep => omdb.flatMap(s.classify(_, c.mediaType, i))
    }).map(_.flatten)

  implicit val decodeClassifier: Decoder[Classifier] = deriveConfiguredDecoder
}

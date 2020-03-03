package mediasort.classify

import cats.effect.IO
import cats.instances.list._
import io.circe.Decoder
import io.circe.generic.extras.semiauto._
import mediasort.config.Config._
import mediasort.clients.OMDB
import fs2.Stream

import scala.util.matching.Regex

case class Classifier(
    mimeTypes: Option[FilterSet[Regex]],
    filename: Option[FilterSet[Regex]],
    criteria: List[ClassifierStep]
) {
  def applies(i: Input) =
    mimeTypes.forall(_.describes(i.mimeTypes)(mimeType => regex => regex.matches(mimeType))) &&
    filename.forall(_.describes(List(i.path.toString))(path => regex => regex.matches(path)))
}

object Classifier {
  def classifications(i: Input, classifiers: List[Classifier], omdb: IO[OMDB]) = for {
    classifier <- Stream.evals(IO.pure(classifiers))
    step <- Stream.emits(classifier.criteria)
    classification <- Stream.eval(step.classify(i, omdb)).unNone
  } yield classification

  implicit val decodeClassifier: Decoder[Classifier] = deriveConfiguredDecoder
}

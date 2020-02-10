package mediasort.classify

import cats.effect.IO
import cats.syntax.traverse._
import cats.syntax.semigroup._
import cats.instances.list._
import cats.instances.option._
import io.circe.Decoder
import io.circe.generic.extras.semiauto._
import mediasort.config.Config._
import ClassifierStep._
import cats.data.{NonEmptyList, OptionT}
import mediasort.io.OMDB
import mediasort.ops._
import fs2.Stream

import scala.util.matching.Regex

case class Classifier(
    mediaType: MediaType,
    mimeTypes: Option[Classifier.FilterSet[Regex]],
    filename: Option[Classifier.FilterSet[Regex]],
    criteria: List[ClassifierStep]
) {
  def applies(i: Input) =
    mimeTypes.forall(_.describes(i.mimeTypes)(mimeType => regex => regex.matches(mimeType))) &&
    filename.forall(_.describes(List(i.path.toString))(path => regex => regex.matches(path)))
}

object Classifier {
  case class FilterSet[A](
      any: Option[NonEmptyList[A]] = None,
      only: Option[NonEmptyList[A]] = None,
      exclude: Option[NonEmptyList[A]] = None
  ) {

    lazy val empty = (any |+| only |+| exclude).isEmpty
    lazy val anyList = any.map(_.toList).getOrElse(List.empty)

    def filter[B](predicate: B => A => Boolean): B => Boolean = b =>
      (only |+| any).fold[Boolean](
        !exclude.exists(_.exists(predicate(b)))
      )(_.exists(predicate(b)))

    // returns true iff the criteria in this instance describe the input list
    // any - for each entry, some entry in list "matches"
    // only - list only contains elements that match some entry in only
    // exclude - list does not contain any elements that match some entry in exclude
    //
    // any - not (for each entry, no element in list matches)
    def describes[B](in: List[B])(predicate: B => A => Boolean): Boolean = empty || {
      // None - one of (only, exclude) did NOT match
      // Some(v)- whether or not "any" matched for this element
      def passes(elem: B): Option[List[Boolean]] = {
        val onlyRes = only.forall(_.exists(predicate(elem)))
        val excludeRes = !exclude.exists(_.exists(predicate(elem)))

        // this is broken - this says "some element matched some entry in `any`
        // you need "for EACH in `any`, some element matched it
        if (onlyRes && excludeRes) Some(anyList.map(predicate(elem)))
        else None
      }

      Stream.emits(in)
        .unchunk
        .map(passes)
        .foldFailFast[Option[List[Boolean]]](_.isEmpty, Some(List.fill(anyList.size)(true)), (state, e) => e match {
        case Some(anyRes) => state.map(_.zip(anyRes).map { case (a, b) => a || b })
        case None => None
      }).take(1).compile.last match {
        case None => true // empty input/unreachable from fold
        case Some(None) => false // some element failed 'only'/'exclude' check
        case Some(a) => !a.contains(false) // if no false entries, each 'any' check passed for some element
      }
    }
  }

  implicit def decodeFilterSet[A: Decoder]: Decoder[FilterSet[A]] = deriveConfiguredDecoder

  def classifications(i: Input, classifiers: List[Classifier], omdb: IO[OMDB]) =
    classifiers.flatTraverse(c =>
      c.criteria.traverse(_.classify(c.mediaType, i, omdb))
    ).map(_.flatten)

  def classificationStream(i: Input, classifiers: List[Classifier], omdb: IO[OMDB]) = for {
    classifier <- Stream.evals(IO.pure(classifiers))
    step <- Stream.emits(classifier.criteria)
    classification <- Stream.eval(step.classify(classifier.mediaType, i, omdb)).unNone
  } yield classification

  implicit val decodeClassifier: Decoder[Classifier] = deriveConfiguredDecoder
}

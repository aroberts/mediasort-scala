package mediasort.classify

import java.nio.file.Path

import cats.data.{EitherT, OptionT}
import cats.instances.option._
import cats.instances.lazyList._
import cats.instances.list._
import cats.instances.either._
import cats.syntax.traverse._
import cats.syntax.show._
import cats.syntax.either._
import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.extras.semiauto._
import mediasort.config.Config
import mediasort.config.Config._
import mediasort.errors._
import mediasort.{paths, strings}
import mediasort.ops._
import fs2._
import mediasort.io.OMDB

import scala.util.matching.Regex

/**
  * Classifiers are made up of ClassifierSteps, each of which can only emit at
  * most one classification per input.
  */
sealed trait ClassifierStep {
  def logError(err: String) = scribe.error(s"${strings.underscore(strings.typeName(this))}: $err")
}

object ClassifierStep {
  sealed trait BasicClassifierStep extends ClassifierStep {
    def classify(mediaType: MediaType, i: Input): IO[Option[Classification]]
  }

  sealed trait OMDBClassifierStep extends ClassifierStep {
    def classify(omdb: OMDB, mediaType: MediaType, i: Input): IO[Option[Classification]]
  }

  case class MimePatternPercent(pattern: Regex, gain: Option[Double]) extends BasicClassifierStep {
    override def classify(mediaType: MediaType, i: Input) = {
      val matches = i.mimeTypes.map(pattern.findFirstMatchIn)
      val score = matches.length * 10.0 / i.mimeTypes.length
      val gained = Math.round(gain.map(_ * score).getOrElse(score)).toInt

      IO.pure(Option(Classification(i.path, mediaType, gained, None)))
    }
  }

  case class PathPatterns(patterns: List[MatcherWithScore]) extends BasicClassifierStep {
    def classify(mediaType: MediaType, i: Input) = {
      val stream = for {
        mws <- Stream.evals(IO.pure(patterns))
        matched <- Stream.emit(mws.pattern.findFirstMatchIn(i.path.toString)).unNone
        safeTitle = mws.titleGroup.traverse(matched.safeGroup("error extracting title:"))
        title <- Stream.eval(IO.pure(safeTitle)).rethrow
      } yield Classification(i.path, mediaType, mws.score, title)

      stream.take(1).compile.last
    }
  }

  /**
    * Generates a Classification from an OMDB query based on the matcher-score
    * pairs passed in as `contentPatterns`, transformed to OMDB queries via
    * `queryFromGroups`, as they are evaluated against the contents of any
    * files matching the extension patterns in `extensions`.
    *
    * This classifier returns the first successful response from OMDB, and stops
    * looking
    */
  case class OMDBQueryFromFileContents(
      extensions: List[String],
      contentPatterns: List[MatcherWithScore],
      queryFromGroups: OMDBQueryFromGroups
  ) extends OMDBClassifierStep {
    override def classify(omdb: OMDB, mediaType: MediaType, i: Input) = {
      val stream = for {
        path <- Stream.emits(i.files).filter(p => extensions.exists(p.endsWith))
        c <- omdbMatchClassifications(omdb, logError, i.path, mediaType, contentPatterns, queryFromGroups)(paths.readFile(path))
      } yield c

      stream.take(1).compile.last
    }
  }

  case class OMDBQueryFromPath(
      contentPatterns: List[MatcherWithScore],
      queryFromGroups: OMDBQueryFromGroups
  ) extends OMDBClassifierStep {
    override def classify(omdb: OMDB, mediaType: MediaType, i: Input) = {
      omdbMatchClassifications(
        omdb, logError, i.path, mediaType, contentPatterns, queryFromGroups
      )(Stream.emit(i.path.toString)).take(1).compile.last
    }
  }

  private def omdbMatchClassifications(
      omdb: OMDB,
      logError: String => Unit,
      path: Path,
      mediaType: MediaType,
      contentPatterns: List[MatcherWithScore],
      queryFromGroups: OMDBQueryFromGroups
  )(input: Stream[IO, String]) = for {
    inputElem <- input
    mws <- Stream.emits(contentPatterns)
    matched <- Stream.emit(mws.pattern.findFirstMatchIn(inputElem)).unNone
    query <- Stream.eval(IO.pure(queryFromGroups.toQuery(matched))).rethrow
    response <- Stream.eval(omdb.query(query))
    filtered <- Stream.emit(response.filter(_.containsAnyType(queryFromGroups.responseTypes))).unNone
  } yield Classification(path, mediaType, mws.score, Some(filtered.title))

  implicit val decodeClassifierStep: Decoder[ClassifierStep] = deriveConfiguredDecoder
}

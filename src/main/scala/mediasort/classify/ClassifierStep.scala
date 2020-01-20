package mediasort.classify

import cats.data.{EitherT, OptionT}
import cats.instances.option._
import cats.instances.lazyList._
import cats.syntax.traverse._
import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.extras.semiauto._
import mediasort.config.Config
import mediasort.config.Config.jsonCfg
import mediasort.strings
import os.Path
import fs2._

sealed trait ClassifierStep
object ClassifierStep {
  implicit val decodeClassifierStep: Decoder[ClassifierStep] = deriveConfiguredDecoder

  sealed trait Initial extends ClassifierStep {
    def classify(owner: Classifier, i: Input)(implicit cfg: Config): IO[Option[Classification]]
  }


  sealed trait Chained extends ClassifierStep {
    def classify(owner: Classifier, i: Input, current: Classification)(implicit cfg: Config): IO[Option[Classification]]
  }

  case class MimePatternPercent(pattern: String) extends Initial {
    override def classify(owner: Classifier, i: Input)(implicit cfg: Config) = {
      i.mimeTypes.map(types => {
        val regex = pattern.r
        val matches = types.flatMap(regex.findFirstMatchIn)
        val score = Math.round(matches.length * 10.0 / types.length).toInt

        Option(Classification(i.path, owner.mediaType, score, Some(i.path.last)))
      })
    }
  }

  case class ContainsMimePattern(pattern: String, boost: Option[Int]) extends Chained {
    override def classify(owner: Classifier, i: Input, current: Classification)(implicit cfg: Config) =
      i.mimeTypes.map(types => {
        val regex = pattern.r
        if (types.exists(regex.findFirstMatchIn(_).isDefined))
          Option(boost.map(b => current.copy(score = Classification.score(current.score + b))).getOrElse(current))
        else None
      })
  }

  case class NotContainsMimePattern(pattern: String) extends Chained {
    override def classify(owner: Classifier, i: Input, current: Classification)(implicit cfg: Config) =
      i.mimeTypes.map(types => {
        val regex = pattern.r
        if (types.exists(regex.findFirstMatchIn(_).isDefined)) None else Option(current)
      })
  }

  case class OMDBQueryFromFileContents(
      extensions: List[String],
      // TODO:
      //  contentPatterns: List[MatcherWithScore],
      contentPatterns: List[String],
      queryFromGroups: OMDBQueryFromGroups,
      score: Int
  ) extends Initial {
    // rewrite this with EitherT[IO, Error, A] where A ends up being Option[Classification]
    // also, fs2 provides a better lazy list
    def extractFirstMatch(path: Path)(implicit cfg: Config) =
      IO(os.read(path)).flatMap(data => {
        EitherT(LazyList.from(contentPatterns)
          // TODO: uncaught exception!
          .flatMap(_.r.findFirstMatchIn(data))
          .map(queryFromGroups.toQuery)
        ).leftMap(err => scribe.error(s"${strings.underscore(strings.typeName(this))}: $err"))
          .collectRight
          .traverse(q =>
            OptionT(cfg.omdbAPI.query(q))
              .filter(_.containsAnyType(queryFromGroups.responseTypes))
              .value
          )
      }).map(_.flatten.headOption)

    def classify(owner: Classifier, i: Input)(implicit cfg: Config) =
      OptionT(i.expandedPaths.flatMap(paths =>
        LazyList.from(paths.filter(p => extensions.contains(p.ext)))
          .traverse(extractFirstMatch)
          .map(_.flatten.headOption)
      )).map(r => Classification(i.path, owner.mediaType, score, Some(r.title)))
        .value
  }

  case class InputPatterns(
      patterns: List[MatcherWithScore]
  ) extends Initial {
    def classify(owner: Classifier, i: Input)(implicit cfg: Config) = {
      val pipe = for {
        mws <- Stream.emits(patterns)
        matched <- Stream(mws.pattern.findFirstMatchIn(i.path.last)).unNone
        // TODO: matched.group can fail
        title = mws.titleGroup.map(matched.group)
      } yield Classification(i.path, owner.mediaType, mws.score, title)

      IO.pure(pipe.take(1).compile.last)
    }
  }

}

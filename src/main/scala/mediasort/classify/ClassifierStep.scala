package mediasort.classify

import cats.data.{EitherT, OptionT}
import cats.instances.option._
import cats.instances.lazyList._
import cats.syntax.traverse._
import cats.effect.IO
import mediasort.config.Config
import mediasort.strings
import os.Path

sealed trait ClassifierStep
object ClassifierStep {

  trait Initial extends ClassifierStep {
    def classify(owner: Classifier, i: Input)(implicit cfg: Config): IO[Option[Classification]]
  }

  trait Chained extends ClassifierStep {
    def classify(owner: Classifier, i: Input, current: Classification)(implicit cfg: Config): IO[Option[Classification]]

  }

  case class MimePatternPercent(pattern: String) extends Initial {
    override def classify(owner: Classifier, i: Input)(implicit cfg: Config) = {
      i.mimeTypes.map(types => {
        val regex = pattern.r
        val matches = types.flatMap(regex.findFirstMatchIn)
        val score = Math.round(matches.length * 10.0 / types.length).toInt

        Option(Classification(i.path, owner.media, score, Some(i.path.last)))
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

  case class ByOMDBFileFilter(
      extensions: List[String],
      contentPatterns: List[String],
      queryFromGroups: OMDBQueryFromGroups,
      score: Int
  ) extends Initial {
    def extractFirstMatch(path: Path)(implicit cfg: Config) =
      IO(os.read(path)).flatMap(data => {
        EitherT(LazyList.from(contentPatterns.map(_.r))
          .flatMap(_.findFirstMatchIn(data))
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
      )).map(r => Classification(i.path, owner.media, score, Some(r.title)))
        .value
  }

}

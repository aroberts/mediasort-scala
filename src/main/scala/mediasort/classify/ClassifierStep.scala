package mediasort.classify

import cats.effect.IO
import mediasort.config.Config

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

}

package mediasort.classify

import mediasort.config.Config

import scala.annotation.tailrec

case class Classifier(
  mediaType: String, // TODO
  criteria: List[ClassifierStep]
) {
  def classification(i: Input)(implicit cfg: Config): Option[Classification] = {
    @tailrec def applyStep(
        current: Option[Classification],
        nextSteps: List[ClassifierStep]
    )(implicit cfg: Config): Option[Classification] = current match {
      case None => None
      case a @ Some(_) => nextSteps.headOption match {
        case None => a
        case Some(next) => applyStep(next.classify(this, i, current), nextSteps.tail)
      }
    }

    applyStep(Some(Classification.none(i.path)), criteria)
  }
}

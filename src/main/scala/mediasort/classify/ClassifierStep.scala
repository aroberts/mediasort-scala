package mediasort.classify

import mediasort.config.Config

sealed trait ClassifierStep {
  def classify(owner: Classifier, i: Input, current: Option[Classification])(implicit cfg: Config): Option[Classification]
}

object ClassifierStep {

}

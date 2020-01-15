package mediasort.classify

import cats.Monad
import cats.effect.IO
import mediasort.config.Config

case class Classifier(
  mediaType: String, // TODO
  criteria: List[ClassifierStep]
) {
  lazy val media: MediaType = ???
  def classification(i: Input)(implicit cfg: Config): IO[Option[Classification]] =
    Monad[IO].tailRecM((Option(Classification.none(i.path)), criteria)) {
      // No further steps are evaluated if the current classification falls to None
      case (None, _) => IO.pure(Right(None))
      case (exhausted @ Some(_), Nil) => IO.pure(Right(exhausted))
      case (Some(_), (next: ClassifierStep.Initial) :: rest) =>
        next.classify(this, i).map(res => Left((res, rest)))
      case (Some(current), (next: ClassifierStep.Chained) :: rest) =>
        next.classify(this, i, current).map(res => Left((res, rest)))

      // if there were an indirection trait that the steps returned, each
      // classifier could potentially return its own data
    }

}

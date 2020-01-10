package mediasort

import mediasort.classify.{Classification, MediaType, MimeType}
import mediasort.config.CLIArgs
import cats.effect._
import cats.syntax.traverse._
import cats.syntax.either._
import cats.instances.list._

object Mediasort {
  def main(args: Array[String]): Unit = {
    val parsed = new CLIArgs(args.toIndexedSeq)

    implicit val config = parsed.config()

    // take path
    val input = parsed.path()

    val proc = for {
      // expand input path
      expanded <- MimeType.mimedPaths(input)
      mimeTypes = expanded.map(_.mimeType)
      hasVideo = mimeTypes.exists(_.contains("video"))
      hasAudio = mimeTypes.exists(_.contains("audio"))

      // classify input
      nfos <- MediaType.fromNFOs(input, expanded)
      tv <- if (hasVideo) MediaType.TV.detect(input, expanded) else IO.pure(None)
      movie <- if (hasVideo) MediaType.Movie.detect(input, expanded) else IO.pure(None)
      music <- if (hasAudio) MediaType.Music.detect(input, expanded) else IO.pure(None)
      lossless <- if (hasAudio) MediaType.LosslessMusic.detect(input, expanded) else IO.pure(None)

      // choose highest score
      classification = (nfos ++ tv ++ movie ++ music ++ lossless)
        .sortBy(_.score)
        .reverse
        .headOption
        .getOrElse(Classification.none(input))
        // TODO: scatter some logging in here

      // perform appropriate action
      _ <- config.actionsFor(classification)
        .map(_.perform(parsed.dryRun.getOrElse(false))(input))
        .sequence
    } yield ()

    proc.attempt.unsafeRunSync.leftMap(e => println(e.getMessage))
  }

}

package mediasort

import mediasort.classify.{Classification, MediaType, MimeType}
import mediasort.config.{CLIArgs, Config}
import cats.effect._
import cats.syntax.foldable._
import cats.syntax.either._
import cats.syntax.show._
import cats.instances.list._
import scribe.format._

object Mediasort {
  def fatal(prefix: String)(e: Throwable) = {
    scribe.error(List(prefix, strings.errorMessage(e)).mkString(" "))
    sys.exit(1)
  }

  def main(args: Array[String]): Unit = {
    val parsed = new CLIArgs(args.toIndexedSeq)

    val logLevel =
      if (parsed.verbose.getOrElse(false)) scribe.Level.Debug
      else if (parsed.quiet.getOrElse(false)) scribe.Level.Warn
      else scribe.Level.Info

    scribe.Logger.root.clearHandlers().clearModifiers()
      .withHandler(
        formatter = formatter"$date $level $message",
        minimumLevel = Some(logLevel)
      ).replace()

    implicit val config = Config.load(parsed.config())
      .fold(fatal("Error parsing config:"), identity)

    // take path
    val input = parsed.path()

    val proc = for {
      // expand input path
      expanded <- MimeType.mimedPaths(input)
      mimeTypes = expanded.map(_.mimeType)
      hasVideo = mimeTypes.exists(_.contains("video"))
      hasAudio = mimeTypes.exists(_.contains("audio"))
      dryRun = parsed.dryRun.getOrElse(false)

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

      _ = scribe.debug(classification.show)

      // perform appropriate actions
      actions = config.actionsFor(classification)
      _ = scribe.debug(s"${actions.length} matching actions")
      _ <- actions.foldLeftM(classification)((c, action) => action.perform(dryRun)(c))

    } yield ()

    proc.attempt.unsafeRunSync.leftMap(fatal(s"Error sorting '$input':"))
  }

}

package mediasort

import mediasort.classify.{Classification, Input, MediaType, MimeType}
import mediasort.config.{CLIArgs, Config}
import mediasort.io.Logging
import cats.effect._
import cats.syntax.foldable._
import cats.syntax.either._
import cats.syntax.traverse._
import cats.syntax.show._
import cats.instances.list._

object Mediasort {
  def version = Option(getClass.getPackage.getImplementationVersion).getOrElse("dev")
  def fatal(prefix: String)(e: Throwable) = {
    scribe.error(List(prefix, strings.errorMessage(e)).mkString(" "))
    sys.exit(1)
  }

  def main(args: Array[String]): Unit = {
    val parsed = new CLIArgs(args.toIndexedSeq)

    implicit val config = Config.load(parsed.config())
      .fold(fatal("Error parsing config:"), identity)

    Logging.configure(parsed)

    // take path
    val input = Input(parsed.path())
    val dryRun = parsed.dryRun.getOrElse(false)

    val proc = for {
      // get all classifications produced by conf
      classifications <- config.classifiers.traverse(_.classification(input)).map(_.flatten)
      _ = scribe.debug(classifications.show)

      // choose highest score
      classification = classifications
        .sortBy(_.score)(Ordering[Int].reverse)
        .headOption
        .getOrElse(Classification.none(input.path))

      _ = scribe.debug(classification.show)

      // perform appropriate actions
      actions = config.actionsFor(classification)
      _ = scribe.debug(s"${actions.length} matching actions")
      _ <- actions.foldLeftM(classification)((c, action) => action.perform(dryRun)(c))

    } yield ()

    proc.attempt.unsafeRunSync.leftMap(fatal(s"Error sorting '$input':"))
  }

}

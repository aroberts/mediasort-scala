package mediasort

import mediasort.classify.{Classification, Classifier, Input, MediaType, MimeType}
import mediasort.config.{CLIArgs, Config}
import mediasort.io.Logging
import cats.effect._
import cats.syntax.foldable._
import cats.syntax.either._
import cats.syntax.traverse._
import cats.syntax.show._
import cats.instances.list._
import mediasort.errors._
import fs2.Stream
import cats.syntax.functor._

object Mediasort extends IOApp {
  def version = Option(getClass.getPackage.getImplementationVersion).getOrElse("dev")
  def program(args: CLIArgs): Stream[IO, Unit] = for {
    cfg <- Config.load(args.configPath)
    input <- Stream.eval(Input(args.inputPath))

    omdb <- Stream.eval(cfg.omdbRef)

    classifiers = cfg.classifiers.filter(_.applies(input))
    classifications = Classifier.classifications(input, classifiers, omdb).map(Classification.mergeByType)

  } yield ()

  def run(args: List[String]) = {
    CLIArgs.parser.parse(args) match {
      case Left(help) => IO(System.err.println(help)).as(ExitCode.Success)
      case Right(other) => other match {
        case Left(_) => IO(System.err.println(s"mediasort v$version")).as(ExitCode.Success)
        case Right(parsed) =>
          Logging.configure(parsed.logLevel, parsed.logPath)

          program(parsed)
            .compile
            .drain
            .as(ExitCode.Success)
            .handleErrorWith(e => IO(scribe.error(e.show)).as(ExitCode.Error))
      }
    }
  }

//    implicit val config = Config.load(parsed.config())
//      .fold(fatal("Error parsing config:"), identity)
//
//    Logging.configure(parsed)
//
//    // take path
//    val input = Input(parsed.path())
//    val dryRun = parsed.dryRun.getOrElse(false)
//
//    val proc = for {
//      // get all classifications produced by conf
//      classifications <- config.classifiers.traverse(_.classification(input)).map(_.flatten)
//      _ = scribe.debug(classifications.show)
//
//      // choose highest score
//      classification = classifications
//        .sortBy(_.score)(Ordering[Int].reverse)
//        .headOption
//        .getOrElse(Classification.none(input.path))
//
//      _ = scribe.debug(classification.show)
//
//      // perform appropriate actions
//      actions = config.actionsFor(classification)
//      _ = scribe.debug(s"${actions.length} matching actions")
//      _ <- actions.foldLeftM(classification)((c, action) => action.perform(dryRun)(c))
//
//    } yield ()
//
//    proc.attempt.unsafeRunSync.leftMap(fatal(s"Error sorting '$input':"))

}

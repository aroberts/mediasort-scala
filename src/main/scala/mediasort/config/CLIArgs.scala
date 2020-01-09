package mediasort.config

import java.io.File

import org.rogach.scallop._
import os.Path

import cats.syntax.either._

class CLIArgs(arguments: Seq[String]) extends ScallopConf(arguments) {
  implicit val convertPath: ValueConverter[Path] = implicitly[ValueConverter[String]]
    .flatMap(p => Either.catchNonFatal(Some(Path(p))).leftMap(_.getMessage))

  version(s"mediasort v${Option(getClass.getPackage.getImplementationVersion).getOrElse("dev")}")
  banner(
    """Act on filesystem paths based on rules
      |
      |Usage: mediasort [OPTION [OPTION ...]] <path>
      |
      |Options:
      |""".stripMargin
  )


  val configPath = opt[Path]("config", descr = "path to config.yml", argName = "path", required = true)
  val dryRun = opt[Boolean]("dry-run", descr = "don't make any filesystem changes")
  val quiet = opt[Boolean]("quiet", descr = "less logging")
  val verbose = opt[Boolean]("verbose", descr = "more logging")
  val path = trailArg[File]("path", descr = "path to act on")

  // TODO: check for symlink support
  validate(configPath)(p => if (!os.isFile(p)) Left("config must be a file") else Right(()))
  mutuallyExclusive(quiet, verbose)

  verify
}

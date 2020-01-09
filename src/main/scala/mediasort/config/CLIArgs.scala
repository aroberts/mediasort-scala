package mediasort.config

import java.io.File

import org.rogach.scallop._
import os.Path
import cats.syntax.either._
import mediasort.strings

class CLIArgs(arguments: Seq[String]) extends ScallopConf(arguments) {

  version(s"mediasort v${Option(getClass.getPackage.getImplementationVersion).getOrElse("dev")}")
  banner(
    """Act on filesystem paths based on rules
      |
      |Usage: mediasort [OPTION [OPTION ...]] <path>
      |
      |Options:
      |""".stripMargin
  )

  // TODO: check for symlink support
  val config = opt[Config]("config", descr = "path to config.yml", argName = "path", required = true)
  val dryRun = opt[Boolean]("dry-run", descr = "don't make any filesystem changes")
  val quiet = opt[Boolean]("quiet", descr = "less logging")
  val verbose = opt[Boolean]("verbose", descr = "more logging")
  val path = trailArg[File]("path", descr = "path to act on")

  mutuallyExclusive(quiet, verbose)

  verify
}

object CLIArgs {
  val convertString = implicitly[ValueConverter[String]]
  implicit val convertConfig: ValueConverter[Config] = convertString.flatMap(Config.load)
  implicit val convertPath: ValueConverter[Path] = convertString.flatMap(p =>
    Either.catchNonFatal(Some(os.Path(p))).leftMap(strings.errorMessage())
  )
}

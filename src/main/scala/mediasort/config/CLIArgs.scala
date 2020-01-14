package mediasort.config

import org.rogach.scallop._
import os.Path
import cats.syntax.either._
import mediasort.{Mediasort, paths, strings}
import CLIArgs._

class CLIArgs(arguments: Seq[String]) extends ScallopConf(arguments) {

  version(s"mediasort v${Mediasort.version}")
  banner(
    """Act on filesystem paths based on rules
      |
      |Usage: mediasort [OPTION [OPTION ...]] <path>
      |
      |Options:
      |""".stripMargin
  )

  val config = opt[Path]("config", descr = "path to config.yml", argName = "path", required = true)
  val dryRun = opt[Boolean]("dry-run", descr = "don't make any filesystem changes")
  val quiet = opt[Boolean]("quiet", descr = "less logging")
  val verbose = opt[Boolean]("verbose", descr = "more logging")
  val path = trailArg[Path]("path", descr = "path to act on", required = false)

  mutuallyExclusive(quiet, verbose)

  verify
}

object CLIArgs {
  implicit val convertPath: ValueConverter[Path] = implicitly[ValueConverter[String]].flatMap(p =>
    Either.catchNonFatal(Some(paths.path(p))).leftMap(strings.errorMessage)
  )
}

package mediasort.config

import java.nio.file.Path

import org.rogach.scallop._
import mediasort.Mediasort


class CLIArgs(arguments: List[String]) extends ScallopConf(arguments) {

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
  val dryRun = opt[Boolean]("dry-run", descr = "log actions instead of performing them")
  val quiet = opt[Boolean]("quiet", descr = "less logging")
  val verbose = opt[Boolean]("verbose", descr = "more logging")
  val path = trailArg[Path]("path", descr = "path to act on", required = false)

  mutuallyExclusive(quiet, verbose)

  verify
}

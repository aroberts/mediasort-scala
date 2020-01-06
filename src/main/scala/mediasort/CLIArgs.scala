package mediasort

import java.io.File

import org.rogach.scallop._

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

  val configPath = opt[File]("config", descr = "path to config.yml", argName = "path", required = true)
  val dryRun = opt[Boolean]("dry-run", descr = "don't make any filesystem changes")
  val quiet = opt[Boolean]("quiet", descr = "less logging")
  val verbose = opt[Boolean]("verbose", descr = "more logging")
  val path = trailArg[File]("path", descr = "path to act on")

  validateFileIsFile(configPath)
  mutuallyExclusive(quiet, verbose)

  verify
}


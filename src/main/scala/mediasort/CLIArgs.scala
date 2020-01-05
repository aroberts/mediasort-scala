package mediasort

import java.io.File

import org.rogach.scallop._

class CLIArgs(arguments: Seq[String]) extends ScallopConf(arguments) {
  version(s"mediasort v${Option(getClass.getPackage.getImplementationVersion).getOrElse("dev")}")
  banner(
    """Usage: mediasort [OPTION [OPTION ...]] <COMMAND> [COMMAND-ARGS]
      |Act on filesystem paths based on rules
      |Options:
      |""".stripMargin
  )

  // TODO: require config path only when running a subcommand
  val configPath = opt[File]("config", descr = "path to config.yml")
  val dryRun = opt[Boolean]("dry-run", descr = "don't make any filesystem changes")
  val quiet = opt[Boolean]("quiet", descr = "less logging")
  val verbose = opt[Boolean]("verbose", descr = "more logging")

  validateFileIsFile(configPath)
  mutuallyExclusive(quiet, verbose)

  verify
}

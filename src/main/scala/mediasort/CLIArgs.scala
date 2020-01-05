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
  val validateCfg = opt[Boolean]("validate", descr = "validate config and exit")
  val path = trailArg[File]("path", descr = "path to act on", required = false)

  validateFileIsFile(configPath)
  mutuallyExclusive(quiet, verbose)
  validateOpt(validateCfg, path) {
    case (Some(false), None) => Left("must provide a path to act on")
    case _ => Right(())
  }

  verify
}


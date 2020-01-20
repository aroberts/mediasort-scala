package mediasort.config

import java.nio.file.Path

import cats.data.Validated
import scribe.Level
import com.monovore.decline._
import cats.syntax.apply._


case class CLIArgs(configPath: Path, logLevel: Level, dryRun: Boolean, inputPath: Path)
object CLIArgs {
  val config = Opts.option[Path]("config", help = "path to config.yml", short = "c", metavar = "path")
  val dryRun = Opts.flag("dry-run", help = "log actions instead of performing them").orFalse
  val quiet = Opts.flag("quiet", help = "less logging", short = "q").orFalse
  val verbose = Opts.flag("verbose", help = "more logging", short = "v").orFalse
  val version = Opts.flag("version", help = "print version and exit", visibility = Visibility.Partial)
  val path = Opts.argument[Path]("input path")

  val logLevel: Opts[Level] = (verbose, quiet).tupled.mapValidated {
    case (true, false) => Validated.valid(Level.Debug)
    case (false, false) => Validated.valid(Level.Info)
    case (false, true) => Validated.valid(Level.Warn)
    case _ => Validated.invalidNel(s"Can't pass both $quiet and $verbose")
  }

  val parser = Command("mediasort", "Act on filesystem paths based on rules") {
    version.map(Left(_)) orElse
      (config, logLevel, dryRun, path).mapN(CLIArgs.apply).map(Right(_))
  }
}

//class CLIArgs(arguments: List[String]) extends ScallopConf(arguments) {
//
//  version(s"mediasort v${Mediasort.version}")
//  banner(
//    """Act on filesystem paths based on rules
//      |
//      |Usage: mediasort [OPTION [OPTION ...]] <path>
//      |
//      |Options:
//      |""".stripMargin
//  )
//
//  val config = opt[Path]("config", descr = "path to config.yml", argName = "path", required = true)
//  val dryRun = opt[Boolean]("dry-run", descr = "log actions instead of performing them")
//  val quiet = opt[Boolean]("quiet", descr = "less logging")
//  val verbose = opt[Boolean]("verbose", descr = "more logging")
//  val path = trailArg[Path]("path", descr = "path to act on", required = false)
//
//  mutuallyExclusive(quiet, verbose)
//
//  verify
//
//  lazy val logLevel = (quiet.toOption, verbose.toOption) match {
//    case (Some(true), _) => Level.Warn
//    case (_, Some(true)) => Level.Debug
//    case _ => Level.Info
//  }
//}

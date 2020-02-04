package mediasort.config

import java.nio.file.Path

import cats.data.Validated
import scribe.Level
import com.monovore.decline._
import cats.syntax.apply._


case class CLIArgs(configPath: Path, logPath: Option[Path], logLevel: Level, dryRun: Boolean, inputPath: Path)
object CLIArgs {
  val config = Opts.option[Path]("config", help = "path to config.yml", short = "c", metavar = "path")
  val log = Opts.option[Path]("log", help = "log here as well as stdout", short = "l", metavar = "path").orNone
  val dryRun = Opts.flag("dry-run", help = "log actions instead of performing them", short = "d").orFalse
  val quiet = Opts.flags("quiet", help = "less logging", short = "q").withDefault(0)
  val verbose = Opts.flags("verbose", help = "more logging", short = "v").withDefault(0)
  val version = Opts.flag("version", help = "print version and exit", visibility = Visibility.Partial)
  val path = Opts.argument[Path]("input path")

  val logLevel: Opts[Level] = (verbose, quiet).tupled.mapValidated {
    case (0, 0) => Validated.valid(Level.Info)
    case (1, 0) => Validated.valid(Level.Debug)
    case (_, 0) => Validated.valid(Level.Trace)
    case (0, 1) => Validated.valid(Level.Warn)
    case (0, _) => Validated.valid(Level.Error)
    case _ => Validated.invalidNel("Can't pass both --quiet and --verbose")
  }

  val parser = Command("mediasort", "Act on filesystem paths based on rules") {
    version.map(Left(_)) orElse
      (config, log, logLevel, dryRun, path).mapN(CLIArgs.apply).map(Right(_))
  }
}

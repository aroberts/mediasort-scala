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
      (config, log, logLevel, dryRun, path).mapN(CLIArgs.apply).map(Right(_))
  }
}

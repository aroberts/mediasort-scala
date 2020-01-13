package mediasort.io

import mediasort.config.{CLIArgs, Config}
import scribe.Level
import scribe.format._
import scribe.handler.LogHandler
import scribe.writer.FileWriter

object Logging {

  def configure(args: CLIArgs)(implicit cfg: Config): Unit = {
    val logLevel: Level =
      if (args.verbose.getOrElse(false)) Level.Debug
      else if (args.quiet.getOrElse(false)) Level.Warn
      else Level.Info

    val logFormat = formatter"$date $level $message"
    val console = LogHandler(formatter = logFormat, minimumLevel = Some(logLevel))
    val file = cfg.logPath.map(p => console.withWriter(
      FileWriter().path(_ => p.toNIO).append
    ))

    List(Some(console), file).flatten
      .foldLeft(scribe.Logger.root.clearHandlers().clearModifiers())(_.withHandler(_))
      .replace()
  }

}

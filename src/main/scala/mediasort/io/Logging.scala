package mediasort.io

import java.nio.file.Path

import scribe.Level
import scribe.format._
import scribe.handler.LogHandler
import scribe.writer.FileWriter

object Logging {

  def configure(logLevel: Level, logPath: Option[Path]): Unit = {
    val logFormat = formatter"$date $level $message"
    val console = LogHandler(formatter = logFormat, minimumLevel = Some(logLevel))
    val file = logPath.map(p => console.withWriter(FileWriter().path(_ => p).append))

    List(Some(console), file).flatten
      .foldLeft(scribe.Logger.root.clearHandlers().clearModifiers())(_.withHandler(_))
      .replace()
  }

}

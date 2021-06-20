package mediasort.clients

import java.nio.file.Path
import scribe.{Level, LogRecord, Priority}
import scribe.format._
import scribe.handler.LogHandler
import scribe.modify.LogModifier
import scribe.writer.FileWriter

object Logging {

  def silenceOtherLoggers: LogModifier = new LogModifier {
    override def priority = Priority.High
    override def apply[M](record: LogRecord[M]) =
      if (record.className.startsWith("mediasort")) Some(record) else None
  }

  def configure(logLevel: Level, logPath: Option[Path]): Unit = {
    val logFormat = formatter"$date $level $message"
    val modifiers = if (logLevel == Level.Trace) Nil else silenceOtherLoggers :: Nil
    val console = LogHandler(formatter = logFormat, minimumLevel = Some(logLevel), modifiers = modifiers)
    val file = logPath.map(p => console.withWriter(FileWriter().path(_ => p).append))

    List(Some(console), file).flatten
      .foldLeft(scribe.Logger.root.clearHandlers().clearModifiers())(_.withHandler(_))
      .replace()
  }

}

package mediasort.classify

import java.nio.file._
import mediasort.paths

case class Input(path: Path) {
  lazy val filename = path.getFileName.toString
  lazy val files = paths.expandFiles(path)

  lazy val mimedPaths = files.map(p => MimeType.MimedPath(p, MimeType(p)))
  lazy val mimeTypes = mimedPaths.map(_.mimeType)
}

package mediasort.classify

import os.Path

case class Input(path: Path) {
  lazy val mimedPaths = MimeType.mimedPaths(path)
  lazy val mimeTypes = mimedPaths.map(_.map(_.mimeType))
}

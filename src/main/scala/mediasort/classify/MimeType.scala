package mediasort.classify

import cats.effect.IO
import os.Path
import javax.activation
import mediasort.paths

object MimeType {
  val impl = new activation.MimetypesFileTypeMap()

  def apply(s: String): String = impl.getContentType(s)
  def apply(s: Path): String = apply(s.last)

  case class MimedPath(path: Path, mimeType: String)
  def mimedPaths(in: Path) =
    paths.expandFiles(in).map(_.map(p => MimedPath(p, apply(p))))

}

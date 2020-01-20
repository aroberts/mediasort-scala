package mediasort.classify

import java.nio.file.Path

import javax.activation

object MimeType {
  val impl = new activation.MimetypesFileTypeMap()

  def apply(filename: String): String = impl.getContentType(filename)
  def apply(p: Path): String = apply(p.getFileName.toString)

  case class MimedPath(path: Path, mimeType: String)
}

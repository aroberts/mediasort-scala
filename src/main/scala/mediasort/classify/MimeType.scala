package mediasort.classify

import os.Path

import javax.activation

object MimeType {
  val impl = new activation.MimetypesFileTypeMap()

  def apply(s: String): String = impl.getContentType(s)
  def apply(s: Path): String = apply(s.last)
}

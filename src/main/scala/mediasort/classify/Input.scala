package mediasort.classify

import java.nio.file._

import mediasort.paths
import cats.effect._

case class Input private (path: Path, files: List[Path], mimedPaths: List[MimeType.MimedPath]) {
  lazy val filename = path.getFileName.toString
  lazy val mimeTypes = mimedPaths.map(_.mimeType)
}

object Input {
  def apply(path: Path): IO[Input] =
    paths.expandFiles(path).compile.toList.map(files => Input(
      path.toAbsolutePath.normalize,
      files,
      // TODO: it would be great if this was also created on-demand rather than at construction time
      files.map(p => MimeType.MimedPath(p, MimeType(p)))
    ))
}

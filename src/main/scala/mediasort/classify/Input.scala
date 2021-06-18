package mediasort.classify

import java.nio.file._
import mediasort.paths
import mediasort.config.Config._
import cats.effect._


import io.circe.Decoder
import io.circe.generic.extras.semiauto._

import scala.util.matching.Regex

case class Input private (path: Path, files: List[Path]) {
  lazy val filename = path.getFileName.toString

  lazy val mimedPaths = files.map(p => MimeType.MimedPath(p, MimeType(p)))
  lazy val mimeTypes = mimedPaths.map(_.mimeType)
}

object Input {
  case class Rewriter(pattern: Regex, replacement: String) {
    def apply(path: Path): Path =
      Paths.get(pattern.replaceAllIn(path.toString, replacement))
  }
  object Rewriter {
    implicit val decodeInputRewriter: Decoder[Rewriter] = deriveConfiguredDecoder
  }

  def apply(path: Path, rewriters: List[Rewriter]): IO[Input] = {
    val pathWithRewrites = rewriters.foldLeft(path) { case (p, r) => r(p) }

    paths.expandFiles(pathWithRewrites).compile.toList.map(files =>
      Input(pathWithRewrites.toAbsolutePath.normalize, files)
    )
  }
}

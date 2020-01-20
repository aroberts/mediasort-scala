package mediasort

import java.nio.file.{FileVisitOption, Files, Path}

import cats.effect.{Blocker, IO}
import fs2.io.file
import fs2.Stream
import mediasort.io.contextShift

package object paths {
  def walk(p: Path) = Stream.resource(Blocker[IO]).flatMap(b =>
    file.walk[IO](b, p, Seq(FileVisitOption.FOLLOW_LINKS))
  )

  def expandFiles(p: Path) = walk(p).filter(Files.isRegularFile(_))
  def expandDirs(p: Path) = walk(p).filter(Files.isDirectory(_))
}

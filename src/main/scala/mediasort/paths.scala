package mediasort

import java.nio.file.{CopyOption, FileVisitOption, Files, LinkOption, Path}

import cats.effect.{Blocker, IO}
import fs2.io.file
import fs2.{Stream, text}
import mediasort.io.contextShift

object paths {
  def walk(p: Path) = Stream.resource(Blocker[IO]).flatMap(b =>
    file.walk[IO](b, p, Seq(FileVisitOption.FOLLOW_LINKS))
  )

  def expandFiles(p: Path) = walk(p).filter(Files.isRegularFile(_))
  def expandDirs(p: Path) = walk(p).filter(Files.isDirectory(_))

  def readFile(p: Path) = Stream.resource(Blocker[IO]).flatMap(b =>
    file.readAll[IO](p, b, 4096)
    .through(text.utf8Decode)
  )

  def copy(src: Path, dst: Path, flags: Seq[CopyOption] = Seq.empty) = (for {
    b <- Stream.resource(Blocker[IO])
    currentFile <- file.walk[IO](b, src) // don't follow links in a copy operation
    targetFile = dst.resolve(src.relativize(currentFile))
    // TODO: Log
    _ <- Stream.eval(file.copy[IO](b, currentFile, targetFile, flags))
  } yield ()).compile.drain

  def link(src: Path, dst: Path) = (for {
    b <- Stream.resource(Blocker[IO])
    currentFile <- file.walk[IO](b, src).filter(Files.isRegularFile(_, LinkOption.NOFOLLOW_LINKS))
    targetFile = dst.resolve(src.relativize(currentFile))
    // TODO: Log
    _ <- Stream.eval(b.delay[IO, Path](Files.createLink(targetFile, currentFile)))
  } yield ()).compile.drain


}

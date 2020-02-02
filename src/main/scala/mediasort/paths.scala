package mediasort

import java.nio.file.attribute.{PosixFilePermission => PFP}
import java.nio.file.{CopyOption, FileVisitOption, Files, LinkOption, Path}

import cats.effect.{Blocker, IO}

import cats.syntax.either._
import cats.syntax.traverse._
import cats.instances.option._

import scala.jdk.javaapi.CollectionConverters._

import fs2.io.file
import fs2.{Stream, text}
import mediasort.io.contextShift

object paths {
  def walk(p: Path) = Stream.resource(Blocker[IO]).flatMap(b =>
    file.walk[IO](b, p, Seq(FileVisitOption.FOLLOW_LINKS))
  )

  def list(p: Path, filter: Path => Boolean = _ => true) =
    Stream.resource(Blocker[IO]).flatMap(b => file.directoryStream[IO](b, p, filter))

  def expandFiles(p: Path) = walk(p).filter(Files.isRegularFile(_))
  def expandDirs(p: Path) = walk(p).filter(Files.isDirectory(_))

  def readFile(p: Path) = Stream.resource(Blocker[IO]).flatMap(b =>
    file.readAll[IO](p, b, 4096)
    .through(text.utf8Decode)
  )

  def copy(src: Path, dst: Path, perms: Option[Set[PFP]], dryRun: Boolean, flags: Seq[CopyOption] = Seq.empty) = (for {
    b <- Stream.resource(Blocker[IO])
    _ = scribe.info(s"copying '$src' to '$dst'")
    // TODO: do these walk calls preserve the dir structure right?
    currentFile <- file.walk[IO](b, src) // don't follow links in a copy operation
    targetFile = dst.resolve(src.relativize(currentFile))
    _ = scribe.debug(s" - copying '$currentFile' to '$targetFile'")
    _ <- Stream.eval(if (dryRun) IO.pure(()) else file.copy[IO](b, currentFile, targetFile, flags))
    _ <- perms.filter(_ => !dryRun).traverse(p => Stream.eval(setPermissions(b, targetFile, p)))
  } yield ()).compile.drain

  def link(src: Path, dst: Path, perms: Option[Set[PFP]], dryRun: Boolean) = (for {
    b <- Stream.resource(Blocker[IO])
    _ = scribe.info(s"linking '$src' to '$dst'")
    currentFile <- file.walk[IO](b, src).filter(Files.isRegularFile(_, LinkOption.NOFOLLOW_LINKS))
    targetFile = dst.resolve(src.relativize(currentFile))
    _ = scribe.debug(s" - linking '$currentFile' to '$targetFile'")
    _ <- Stream.eval(if (dryRun) IO.pure(()) else b.delay[IO, Path](Files.createLink(targetFile, currentFile)))
    _ <- perms.filter(_ => !dryRun).traverse(p => Stream.eval(setPermissions(b, targetFile, p)))
  } yield ()).compile.drain

  def posixFilePermissions(base10perms: Int) = {
    def perms(base10: Int, r: PFP, w: PFP, x: PFP) =
      if (base10 > 7 || base10 < 0) Left(()) else
        Right(
          Set(
            if ((base10 & 4) == 4) Some(r) else None,
            if ((base10 & 2) == 2) Some(w) else None,
            if ((base10 & 1) == 1) Some(x) else None
          ))

    (for {
      o <- perms(base10perms % 10, PFP.OTHERS_READ, PFP.OTHERS_WRITE, PFP.OTHERS_EXECUTE)
      g <- perms((base10perms / 10) % 10, PFP.GROUP_READ, PFP.GROUP_WRITE, PFP.GROUP_EXECUTE)
      u <- perms((base10perms / 100) % 10, PFP.OWNER_READ, PFP.OWNER_WRITE, PFP.OWNER_EXECUTE)
    } yield (o ++ g ++ u).flatten).leftMap(_ => s"Bad permissions mode: $base10perms")
  }

  def setPermissions(b: Blocker, p: Path, perms: Set[PFP]) =
    b.delay[IO, Path](Files.setPosixFilePermissions(p, asJava(perms)))


}

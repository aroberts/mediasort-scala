package mediasort

import java.nio.file.attribute.{PosixFilePermission => PFP}
import java.nio.file.{CopyOption, FileVisitOption, Files, LinkOption, Path, Paths}
import cats.effect.{Blocker, Concurrent, IO}
import cats.syntax.either._
import cats.syntax.traverse._
import cats.instances.option._

import scala.jdk.javaapi.CollectionConverters._
import fs2.io.{Watcher, file}
import fs2.{Stream, text}
import Mediasort.cs
import mediasort.classify.{FilterSet, Input}

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
      // utf8 chunks
      .through(text.utf8Decode[IO])
      // singleton stream of file content string
      .reduce(_.concat(_))
  )

  def fileTreeOp(
      src: Path,
      dst: Path,
      perms: Option[Set[PFP]],
      preserveDir: Boolean,
      dryRun: Boolean
  )(
      gerund: String,
      generate: (Blocker, Path) => Stream[IO, Path],
      operate: (Blocker, Path, Path) => IO[Path]
  ) = (for {
    b <- Stream.resource(Blocker[IO])
    _ = scribe.info(s"$gerund '$src' to '$dst'")
    targetDir = if (preserveDir && Files.isDirectory(src)) dst.resolve(src.getFileName) else dst
    currentFile <- generate(b, src)
    // handle difference between explicit file (src == current) and generated file from list
    relativeTarget = if (src == currentFile) currentFile.getFileName else src.relativize(currentFile)
    targetFile = targetDir.resolve(relativeTarget)
    _ = scribe.debug(s" - $gerund '$currentFile' to '$targetFile'")
    _ <- Stream.eval(mkdirsIfNecessary(b, targetFile.getParent))
    _ <- Stream.eval(if (dryRun) IO.pure(()) else operate(b, currentFile, targetFile))
    _ <- perms.filter(_ => !dryRun).traverse(p => Stream.eval(setPermissions(b, targetFile, p)))
  } yield ()).compile.drain

  def filteredExtensions(
      filters: Option[FilterSet[String]],
      // directories are not evaluated against the filters param, but they are removed
      // if removeDirs is true
      removeDirs: Boolean = true
  ): Path => Boolean =
    p => if (Files.isDirectory(p)) !removeDirs else
      filters.forall(_.filter[Path](_.toString.endsWith)(p))

  def mkdirsIfNecessary(b: Blocker, targetDir: Path): IO[Unit] = for {
    exists <- file.exists[IO](b, targetDir)
    _ <- if (!exists) {
      scribe.debug(s"- creating directory '$targetDir'")
      file.createDirectories[IO](b, targetDir)
    } else IO.pure(())
  } yield ()

  def copy(
      src: Path,
      dst: Path,
      perms: Option[Set[PFP]],
      extensionFilter: Option[FilterSet[String]],
      preserveDir: Boolean,
      dryRun: Boolean,
      flags: Seq[CopyOption] = Seq.empty
  ) = fileTreeOp(src, dst, perms, preserveDir, dryRun)("copying",
    (b, src) => file.walk[IO](b, src).filter(filteredExtensions(extensionFilter)),
    (b, cur, tgt) => file.copy[IO](b, cur, tgt, flags)
  )

  def link(
      src: Path,
      dst: Path,
      perms: Option[Set[PFP]],
      extensionFilter: Option[FilterSet[String]],
      preserveDir: Boolean,
      dryRun: Boolean
  ) = fileTreeOp(src, dst, perms, preserveDir, dryRun)("linking",
    (b, src) => file.walk[IO](b, src)
       // pre-emptively filter out directories and links - can't be hard linked
      .filter(Files.isRegularFile(_, LinkOption.NOFOLLOW_LINKS))
      .filter(filteredExtensions(extensionFilter)),
    (b, cur, tgt) => b.delay[IO, Path](Files.createLink(tgt, cur))
  )

  def base10posixFilePermissions(base10perms: Int) = {
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


  /**
    * Implements the watchdir semantic.
    *
    * Files placed in the watchdir are read, and each line is treated as a path, from which we
    * attempt to construct an input.
    */
  def watch(path: Path, types: Watcher.EventType*)(implicit c: Concurrent[IO]) = for {
    b <- Stream.resource(Blocker[IO])
    events <- file.watch(b, path, types)
  } yield events
  /**
    * Read the contents of a file and emit a stream of Path objects (one per line of the input)
    *
    * TODO: this will ignore some "files" (symlinks eg?) without notifying the user of the issue
    */
  def extractInputs(path: Path) =
    expandFiles(path)
      .flatMap(readFile)
      .flatMap(contents => Stream.emits(contents.split("\\\\n")))
      .evalMap(line => Input(Paths.get(line)))

}

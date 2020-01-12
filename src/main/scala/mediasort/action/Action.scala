package mediasort.action

import cats.effect.IO
import cats.instances.list._
import cats.syntax.traverse._
import io.circe.Decoder
import io.circe.generic.semiauto._
import mediasort.classify.Classification
import mediasort.{fuzz, paths, strings}
import os._

import scala.util.Try

sealed trait Action {
  def perform(dryRun: Boolean)(input: Classification): IO[Unit]
}
object Action {
  implicit val decodeAction: Decoder[Action] = deriveDecoder
  implicit val decodePath: Decoder[Path] = Decoder[String].emapTry(s => Try(paths.path(s)))
  implicit val decodePermSet: Decoder[PermSet] = Decoder[Int].emapTry(i =>
    Try(PermSet(Integer.parseInt(i.toString, 8)))
  )

  def copyInto(destination: Path, permissions: Option[PermSet], dryRun: Boolean)(input: Path) =
    // TODO: log
    if (dryRun) IO.pure(()) else IO {
      os.copy.into(input, destination, createFolders = true)
      permissions.foreach(os.perms.set(input, _))
    }

  case class CopyTo(destination: Path, permissions: Option[PermSet]) extends Action {
    def perform(dryRun: Boolean)(input: Classification) =
      copyInto(destination, permissions, dryRun)(input.path)
  }

  case class CopyToMatchingSubdir(
      destination: Path,
      permissions: Option[PermSet],
      matchCutoff: Option[Double]
  ) extends Action {
    def chooseSubdir(subdirs: IndexedSeq[Path], name: String) =
      subdirs.map(s => s -> fuzz.ratio(name, strings.normalize(s.last)))
        .filter(_._2 > matchCutoff.getOrElse(.9))
        .sortBy(_._2)(Ordering.Double.IeeeOrdering.reverse)
        .headOption
        .map(_._1)

    def perform(dryRun: Boolean)(input: Classification) = {
      val name = input.normalizedNameOrDir

      paths.expandDirs(destination)
        .map(chooseSubdir(_, name).getOrElse(destination / name))
        .flatMap(copyInto(_, permissions, dryRun)(input.path))
    }
  }

  case class CopyContentsTo(
      destination: Path,
      permissions: Option[PermSet],
      // TODO: do these need to be options?
      only: List[String],
      exclude: List[String],
      preserveDir: Option[Boolean]
  ) extends Action {

    def extFilter(f: Path): Boolean = {
      val ext = f.ext
      (only.nonEmpty && only.contains(ext)) || (only.isEmpty && !exclude.contains(ext))
    }

    override def perform(dryRun: Boolean)(input: Classification) = {
      val target = if (preserveDir.getOrElse(false)) destination / input.path.last else destination

      paths.expandFiles(input.path)
        .flatMap(
          _.filter(extFilter)
            .toList
            .traverse(copyInto(target, permissions, dryRun))
        ).map(_ => ())
    }
  }
}


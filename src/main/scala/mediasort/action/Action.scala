package mediasort.action

import cats.effect.IO
import cats.instances.list._
import cats.syntax.traverse._
import io.circe.Decoder
import io.circe.generic.semiauto._
import mediasort.paths
import os._

import scala.util.Try

sealed trait Action {
  def perform(dryRun: Boolean)(input: Path): IO[Unit]
}
object Action {
  implicit val decodeAction: Decoder[Action] = deriveDecoder
  implicit val decodePath: Decoder[Path] = Decoder[String].map(Path(_))
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
    def perform(dryRun: Boolean)(input: Path) = copyInto(destination, permissions, dryRun)(input)
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

    override def perform(dryRun: Boolean)(input: Path) = {
      val target = if (preserveDir.getOrElse(false)) destination / input.last else destination

      paths.expandFiles(input)
        .flatMap(
          _.filter(extFilter)
            .toList
            .traverse(copyInto(target, permissions, dryRun))
        ).map(_ => ())
    }
  }
}


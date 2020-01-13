package mediasort.action

import cats.data.NonEmptyList
import cats.effect.IO
import cats.instances.list._
import cats.syntax.traverse._
import io.circe.Decoder
import io.circe.generic.extras.semiauto._
import mediasort.classify.Classification
import mediasort.config.Config.jsonCfg
import mediasort.{fuzz, paths, strings}
import os._

import scala.util.Try

sealed trait Action {
  def perform(dryRun: Boolean)(input: Classification): IO[Classification]
}
object Action {
  implicit val decodeAction: Decoder[Action] = deriveConfiguredDecoder
  implicit val decodePath: Decoder[Path] = Decoder[String].emapTry(s => Try(paths.path(s)))
  implicit val decodePermSet: Decoder[PermSet] = Decoder[Int].emapTry(i =>
    Try(PermSet(Integer.parseInt(i.toString, 8)))
  )

  def copyInto(destination: Path, permissions: Option[PermSet], dryRun: Boolean)(input: Path) = {
    val res: Path = destination / input.last

    scribe.info(s"copying '$input' to '$destination'")

    if (dryRun) IO.pure(res) else IO {
      os.copy.into(input, destination, createFolders = true)
      permissions.foreach(os.perms.set(input, _))
      res
    }
  }

  case class CopyTo(destination: Path, permissions: Option[PermSet]) extends Action {
    def perform(dryRun: Boolean)(input: Classification) =
      copyInto(destination, permissions, dryRun)(input.path).map(p => input.copy(path = p))
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
        .map(p => input.copy(path = p))
    }
  }

  case class CopyContentsTo(
      destination: Path,
      permissions: Option[PermSet],
      only: Option[NonEmptyList[String]],
      exclude: Option[NonEmptyList[String]],
      preserveDir: Option[Boolean]
  ) extends Action {
    def extFilter(f: Path): Boolean = {
      val ext = f.ext
      only.fold[Boolean](!exclude.exists(_.contains(ext)))(_.contains(ext))
    }

    override def perform(dryRun: Boolean)(input: Classification) = {
      val target = if (preserveDir.getOrElse(false)) destination / input.path.last else destination

      paths.expandFiles(input.path)
        .flatMap(
          _.filter(extFilter)
            .toList
            .traverse(copyInto(target, permissions, dryRun))
        ).map(_ => input.copy(path = target))
    }

    implicit class NELOps[A](nel: NonEmptyList[A]) {
      def contains(a: A) = nel.head == a || nel.tail.contains(a)
    }
  }
}


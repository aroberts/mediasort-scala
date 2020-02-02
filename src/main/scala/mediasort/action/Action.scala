package mediasort.action

import java.nio.file.{Files, Path}

import cats.data.NonEmptyList
import cats.effect.IO
import cats.instances.list._
import cats.syntax.traverse._
import cats.syntax.show._
import io.circe.Decoder
import io.circe.generic.extras.semiauto._
import mediasort.classify.Classification
import mediasort.config.Config._
import mediasort.io.{Email, Plex}
import mediasort.{fuzz, paths, strings}
import java.nio.file.attribute.{PosixFilePermission => PFP}


sealed trait Action

object Action {
  sealed trait BasicAction extends Action {
    def perform(input: Classification, dryRun: Boolean): IO[Unit]
  }

  sealed trait EmailAction extends Action {
    def perform(input: Classification, dryRun: Boolean, email: Email): IO[Unit]
  }

  sealed trait PlexAction extends Action {
    def perform(input: Classification, dryRun: Boolean, plex: Plex): IO[Unit]
  }


  case class CopyTo(destination: Path, permissions: Option[Set[PFP]]) extends BasicAction {
    def perform(input: Classification, dryRun: Boolean) =
      paths.copy(input.path, destination, None, dryRun)
  }

  case class CopyToMatchingSubdir(
      destination: Path,
      permissions: Option[Set[PFP]],
      matchCutoff: Option[Double]
  ) extends BasicAction {
    def scoreSubdirs(root: Path, name: String) = for {
      dir <- paths.list(root, Files.isDirectory(_))
      dirName = dir.getFileName.toString
      ratio = fuzz.ratio(name, strings.normalize(dirName))
    } yield (dir, ratio)

    def perform(input: Classification, dryRun: Boolean) = {
      val name = input.normalizedNameOrDir

      val target = scoreSubdirs(destination, name)
        .reduce((l, r) => if (l._2 >= r._2) l else r)
        .filter(t => matchCutoff.forall(_ <= t._2))
        .map(_._1)
        .compile.last
        .map(_.getOrElse(destination.resolve(name)))

      target.flatMap(dst => paths.copy(input.path, dst, permissions, dryRun))
    }
  }
//
//  case class CopyContentsTo(
//      destination: Path,
//      permissions: Option[PermSet],
//      only: Option[NonEmptyList[String]],
//      exclude: Option[NonEmptyList[String]],
//      preserveDir: Option[Boolean]
//  ) extends Action {
//    def nelContains[A](a: A)(nel: NonEmptyList[A]) = nel.head == a || nel.tail.contains(a)
//
//    def extFilter(f: Path): Boolean = {
//      val ext = f.ext
//      only.fold[Boolean](!exclude.exists(nelContains(ext)))(nelContains(ext))
//    }
//
//    override def perform(dryRun: Boolean)(input: Classification)(implicit cfg: Config) = {
//      val target = if (preserveDir.getOrElse(false)) destination / input.path.last else destination
//
//      paths.expandFiles(input.path)
//        .flatMap(
//          _.filter(extFilter)
//            .toList
//            .traverse(copyInto(target, permissions, dryRun))
//        ).map(_ => input.copy(path = target))
//    }
//  }
//
//  case class RefreshPlexSection(
//      sectionName: String,
//      force: Option[Boolean]
//  ) extends Action {
//    override def perform(dryRun: Boolean)(input: Classification)(implicit cfg: Config) = if (dryRun) {
//      scribe.info(s"skipping update of plex section '$sectionName'")
//      IO.pure(input)
//    } else cfg.plexAPI.refreshSection(sectionName, force.getOrElse(false)).map(_ => input)
//  }
//
//  case class EmailNotify(to: String, subject: String, body: String) extends Action {
//    def bodyReplacements(input: Classification) = body
//      .replaceAllLiterally("{}", input.show)
//      .replaceAllLiterally("{debug}", input.toString)
//      .replaceAllLiterally("{long}", input.toMultiLineString)
//
//    override def perform(dryRun: Boolean)(input: Classification)(implicit cfg: Config) =
//      cfg.emailAPI.send(to, subject, bodyReplacements(input)).map(_ => input)
//  }
  implicit val decodeAction: Decoder[Action] = deriveConfiguredDecoder
}


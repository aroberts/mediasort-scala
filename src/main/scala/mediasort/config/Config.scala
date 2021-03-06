package mediasort.config

import java.nio.file.{Path, Paths}
import java.nio.file.attribute.{PosixFilePermission => PFP}

import cats.data.NonEmptyList
import cats.syntax.either._
import cats.effect.IO
import fs2.Stream

import io.circe._
import io.circe.generic.extras.semiauto._
import io.circe.generic.extras.Configuration
import io.circe.yaml.parser

import mediasort.action.ActionMatcher
import mediasort.classify.{Classification, Classifier, Input, MediaType}
import mediasort.config.Config._
import mediasort.errors._
import mediasort.paths

import scala.util.Try
import scala.util.matching.Regex


case class Config(
    unclassifiedMediaType: Option[MediaType],
    omdb: Option[OMDBConfig],
    plex: Option[PlexConfig],
    email: Option[EmailConfig],
    inputRewrite: Option[List[Input.Rewriter]],
    classifiers: List[Classifier],
    actions: List[ActionMatcher]
) {
  val unclassified = unclassifiedMediaType.getOrElse(MediaType("other"))

  def actionsFor(c: Classification) = actions.filter(m =>
    m.mediaType == c.mediaType && m.confidence.forall(_ <= c.score)
  ).flatMap(_.perform)
}

object Config {
  case class MediaTypeMapping(input: Regex, output: MediaType)
  case class OMDBConfig(apiKey: Env[String], typeMapping: Option[NonEmptyList[MediaTypeMapping]])
  case class PlexConfig(user: Env[String], password: Env[String], serverAddress: Env[String], port: Option[Int])
  case class EmailConfig(
      from: Env[String],
      host: Env[String],
      port: Env[Int],
      user: Env[String],
      password: Env[String],
      tls: Option[Boolean]
  )

  implicit val jsonCfg = Configuration.default
    .withSnakeCaseMemberNames
    .withSnakeCaseConstructorNames

  implicit val decodeMediaTypeMapping: Decoder[MediaTypeMapping] = deriveConfiguredDecoder

  implicit val decodeConfig: Decoder[Config] = deriveConfiguredDecoder
  implicit val decodeOMDBConf: Decoder[OMDBConfig] = deriveConfiguredDecoder
  implicit val decodePlexConf: Decoder[PlexConfig] = deriveConfiguredDecoder
  implicit val decodeEmailConf: Decoder[EmailConfig] = deriveConfiguredDecoder

  implicit val decodeRegex: Decoder[Regex] = Decoder[String].emapTry(s => Try(s.r.unanchored))
  implicit val decodePermSet: Decoder[Set[PFP]] = Decoder[Int].emap(paths.base10posixFilePermissions)
  implicit val decodePath: Decoder[Path] = Decoder[String].emapTry(s => Try(Paths.get(s)))

  def load[A: Decoder](path: Path): Stream[IO, A] =
    paths.readFile(path)
      .map(parse[A](_, path))
      // halt the stream on error
      .rethrow

  def parse[A: Decoder](data: String, path: Path) =
    parser.parse(data)
      .flatMap(_.as[A])
      .leftMap(reportPrefix(s"error parsing $path"))
}

import com.typesafe.sbt.packager.docker.DockerChmodType

ThisBuild / scalaVersion := "2.13.5"
Global / onChangedBuildSource := ReloadOnSourceChanges

scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-explaintypes", // Explain type errors in more detail.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
  // "-language:experimental.macros", // Allow macro definition (besides implementation and application)
  "-language:higherKinds", // Allow higher-kinded types
  // "-language:implicitConversions", // Allow definition of implicit functions called views
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
  // "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
  // "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
  "-Xlint:option-implicit", // Option.apply used implicit view.
  // "-Xlint:package-object-classes", // Class or object defined in package object.
  "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
  // "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
  "-Ywarn-dead-code", // Warn when dead code is identified.
  "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
  // "-Ywarn-numeric-widen", // Warn when numerics are widened.
  // "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
  // "-Ywarn-unused:imports", // Warn if an import selector is not referenced.
  // "-Ywarn-unused:locals", // Warn if a local definition is unused.
  // "-Ywarn-unused:params", // Warn if a value parameter is unused.
  // "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused.
  // "-Ywarn-unused:privates", // Warn if a private member is unused.
  // "-Ywarn-value-discard", // Warn when non-Unit expression results are unused.
  "-Ybackend-parallelism", "8", // Enable paralellisation — change to desired number!
  "-Ycache-plugin-class-loader:last-modified", // Enables caching of classloaders for compiler plugins
  "-Ycache-macro-class-loader:last-modified", // and macro definitions. This can lead to performance improvements.
  )

lazy val http4sVersion = "0.21.20"
lazy val fs2Version = "2.5.3"
lazy val circeVersion = "0.13.0"
lazy val circeYamlVersion = "0.13.1"
lazy val catsEffectVersion = "2.3.3"


enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)
// breaks -- arg-swallow fix
// enablePlugins(AshScriptPlugin)


packageName := "mediasort"
packageSummary := "Classify and organize your files"
executableScriptName := "mediasort"
maintainer := ""

lazy val mediasort = (project in file("."))
  .settings(
    name := "mediasort",
    version := "0.2.0",
    run / fork := true,

    // don't generate javadoc.jar when running sbt native packager "stage" tasks
    Compile / packageDoc / mappings := Seq(),
    // disable sbt-native-packager wrapper script options
    Universal / javaOptions += "--\n",

    libraryDependencies ++= Seq(
      "com.monovore" %% "decline" % "1.3.0",
      "com.outr" %% "scribe" % "2.7.10",
      "com.outr" %% "scribe-slf4j" % "2.7.10",

      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-generic-extras" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-yaml" % circeYamlVersion,

      "com.sun.mail" % "javax.mail" % "1.6.2",

      "org.scala-lang.modules" %% "scala-xml" % "1.3.0",

      "org.typelevel" %% "cats-effect" % catsEffectVersion,

      "co.fs2" %% "fs2-core" % fs2Version,
      "co.fs2" %% "fs2-io" % fs2Version,

      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.http4s" %% "http4s-scala-xml" % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sVersion,

      "com.codecommit" %% "cats-effect-testing-scalatest" % "0.5.4" % Test,
      "org.scalatest" %% "scalatest" % "3.1.0" % Test
    )
  )
  // publishing-related settings
  .settings(
    // breaks -- arg-swallow fix
    // dockerBaseImage := "openjdk:jre-alpine",
    dockerUpdateLatest := true,
    dockerChmodType := DockerChmodType.Custom("a=rX"),
    dockerAdditionalPermissions := dockerAdditionalPermissions.value.map { case (_, p) =>
      (DockerChmodType.Custom("a+x"), p)
    },
    Docker / packageName := "aroberts/mediasort"
  )

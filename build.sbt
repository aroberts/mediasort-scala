ThisBuild / scalaVersion := "2.13.1"
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
  "-Xlint:nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
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

lazy val mediasort = (project in file("."))
  .settings(
    name := "mediasort",
    fork in run := true,
    libraryDependencies ++= Seq(
      "com.monovore" %% "decline" % "1.0.0",
      "com.outr" %% "scribe" % "2.7.10",

      "io.circe" %% "circe-core" % "0.12.3",
      "io.circe" %% "circe-generic" % "0.12.3",
      "io.circe" %% "circe-generic-extras" % "0.12.2",
      "io.circe" %% "circe-parser" % "0.12.3",
      "io.circe" %% "circe-yaml" % "0.12.0",

      "com.sun.mail" % "javax.mail" % "1.6.2",

      "org.scala-lang.modules" %% "scala-xml" % "2.0.0-M1",

      "org.typelevel" %% "cats-effect" % "2.1.1",

      "co.fs2" %% "fs2-core" % "2.2.2",
      "co.fs2" %% "fs2-io" % "2.2.2",

      "org.http4s" %% "http4s-dsl" % "0.21.1",
      "org.http4s" %% "http4s-circe" % "0.21.1",
      "org.http4s" %% "http4s-scala-xml" % "0.21.1",
      "org.http4s" %% "http4s-blaze-server" % "0.21.1",
      "org.http4s" %% "http4s-blaze-client" % "0.21.1",

      "org.scalatest" %% "scalatest" % "3.1.0" % Test
    )
  )

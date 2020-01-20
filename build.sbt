ThisBuild / scalaVersion := "2.13.1"
Global / onChangedBuildSource := ReloadOnSourceChanges

scalacOptions ++= Seq("-deprecation", "-feature")

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

      "org.scala-lang.modules" %% "scala-xml" % "2.0.0-M1",

      "org.typelevel" %% "cats-effect" % "2.0.0",

      "com.sun.mail" % "javax.mail" % "1.6.2",

      "co.fs2" %% "fs2-core" % "2.1.0",
      "co.fs2" %% "fs2-io" % "2.1.0",

      "com.softwaremill.sttp.client" %% "core" % "2.0.0-RC6",
      "com.softwaremill.sttp.client" %% "circe" % "2.0.0-RC6",
      "com.softwaremill.sttp.client" %% "async-http-client-backend-cats" % "2.0.0-RC6",

      "org.scalatest" %% "scalatest" % "3.1.0" % Test
    )
  )

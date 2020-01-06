ThisBuild / scalaVersion := "2.13.1"

lazy val mediasort = (project in file("."))
  .settings(
    name := "mediasort",
    fork in run := true,
    libraryDependencies ++= Seq(
      "org.rogach" %% "scallop" % "3.3.2",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "io.circe" %% "circe-core" % "0.12.3",
      "io.circe" %% "circe-generic" % "0.12.3",
      "io.circe" %% "circe-parser" % "0.12.3",
      "io.circe" %% "circe-yaml" % "0.10.0",

      "org.scalatest" %% "scalatest" % "3.1.0" % Test
    )
  )

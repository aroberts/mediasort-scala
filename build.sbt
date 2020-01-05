ThisBuild / scalaVersion := "2.13.1"

lazy val mediasort = (project in file("."))
  .settings(
    name := "mediasort",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.1.0" % Test,
      "org.rogach" %% "scallop" % "3.3.2"
    )
  )

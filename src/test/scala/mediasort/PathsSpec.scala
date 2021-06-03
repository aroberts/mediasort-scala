package mediasort

import mediasort.classify.Input

import java.nio.file.Paths
import java.nio.file.attribute.{PosixFilePermissions => PFPs}
import scala.jdk.CollectionConverters._

class PathsSpec extends AsyncSpec {

  def decimalMode(input: Int, string: String) =
    assert(paths.base10posixFilePermissions(input).contains(PFPs.fromString(string).asScala))

  def StringMode(input: String, string: String) =
    assert(paths.base10posixFilePermissions(input.toInt).contains(PFPs.fromString(string).asScala))

  it should "parse file modes" in {
    decimalMode(775, "rwxrwxr-x")
  }

  it should "expandFiles when given a single file" in {
    val file = Paths.get("/Users/aroberts/Headshot.png")

    paths.expandFiles(file).compile.toList
      .asserting(f => assert(f == List(file)))

    Input(file).asserting(_ => assert(true))

  }


}

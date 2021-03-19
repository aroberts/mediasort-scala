package mediasort

import java.nio.file.attribute.{PosixFilePermissions => PFPs, PosixFilePermission => PFP}
import scala.jdk.CollectionConverters._

class PathsSpec extends Spec {

  def decimalMode(input: Int, string: String) =
    assert(paths.base10posixFilePermissions(input).contains(PFPs.fromString(string).asScala))

  def StringMode(input: String, string: String) =
    assert(paths.base10posixFilePermissions(input.toInt).contains(PFPs.fromString(string).asScala))

  it should "parse file modes" in {
    decimalMode(775, "rwxrwxr-x")
  }


}

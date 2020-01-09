package mediasort

import mediasort.classify.MimeType
import mediasort.config.CLIArgs

object Mediasort {
  def main(args: Array[String]): Unit = {
    val parsed = new CLIArgs(args.toIndexedSeq)

    implicit val config = parsed.config()

    // take path
    val input = parsed.path()
    val expanded = MimeType.mimedPaths(input)

    // start with  media types from nfos
    // add MediaTypes from detect() methods
    // collect actions for head media type
    // foreach, apply

  }
}

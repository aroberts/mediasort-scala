package mediasort

import org.rogach.scallop._

class CLIArgs(arguments: Seq[String]) extends ScallopConf(arguments) {
  verify
}

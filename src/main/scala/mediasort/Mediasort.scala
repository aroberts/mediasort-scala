package mediasort

import mediasort.config.CLIArgs

object Mediasort {
  def main(args: Array[String]): Unit = {
    val parsed = new CLIArgs(args.toIndexedSeq)

    parsed.config()

  }
}

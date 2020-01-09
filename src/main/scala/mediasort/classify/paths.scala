package mediasort

import os.Path

package object paths {

  def expandFiles(p: Path): IndexedSeq[Path] =
    if (os.isDir(p)) os.walk(p).filter(os.isFile) else IndexedSeq(p)

}

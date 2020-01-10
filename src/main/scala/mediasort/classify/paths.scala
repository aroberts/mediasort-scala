package mediasort

import cats.effect.IO
import os.Path

package object paths {

  def expandFiles(p: Path): IO[IndexedSeq[Path]] = IO(
    if (os.isDir(p)) os.walk(p).filter(os.isFile) else IndexedSeq(p)
  )

}

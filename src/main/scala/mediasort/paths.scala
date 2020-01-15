package mediasort

import cats.effect.IO
import os.Path

package object paths {

  def expandFiles(p: Path): IO[IndexedSeq[Path]] = IO(
    if (os.isDir(p)) os.walk(p).filter(os.isFile) else IndexedSeq(p)
  )
  def expandDirs(p: Path): IO[IndexedSeq[Path]] = IO(
    if (os.isDir(p)) os.walk(p).filter(os.isDir) else IndexedSeq()
  )

  def path(in: String) = Path(in, os.pwd)

}

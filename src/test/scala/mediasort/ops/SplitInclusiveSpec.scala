package mediasort.ops

import mediasort.Spec
import fs2.{Chunk, Stream}

class SplitInclusiveSpec extends Spec {

  def chunkedAscendingStream(sizes: Int*) = {
    val data = Range(1, sizes.sum + 1).toList
    sizes.foldLeft((data, List.empty[Chunk[Int]])) {
      case ((remaining, chunks), chunkSize) =>
        (remaining.drop(chunkSize), Chunk.iterable(remaining.take(chunkSize)) :: chunks)
    }._2.reverse
      .map(Stream.chunk)
      .reduce(_ ++ _)
  }

  it should "split across chunks of different sizes" in {
    val stream = chunkedAscendingStream(7,4,4,1,3)

    assert(stream.splitInclusive(_ % 3 == 0).toList == List(
      Chunk(1,2),
      Chunk(3,4,5),
      Chunk(6,7,8),
      Chunk(9,10,11),
      Chunk(12,13,14),
      Chunk(15,16,17),
      Chunk(18,19)
    ))
  }

  it should "split across chunks of the same size" in {
    val stream = chunkedAscendingStream(2,2,2,2)
    assert(stream.splitInclusive(_ % 3 == 0).toList == List(
      Chunk(1,2),
      Chunk(3,4,5),
      Chunk(6,7,8)
    ))
  }
  it should "handle tiny chunks" in {
    val stream = chunkedAscendingStream(1,1,1,1,1,1,1,1)
    assert(stream.splitInclusive(_ % 3 == 0).toList == List(
      Chunk(1,2),
      Chunk(3,4,5),
      Chunk(6,7,8)
    ))
  }

  it should "handle no splits with tiny chunks" in {
    val stream = chunkedAscendingStream(1,1,1,1,1,1,1,1)
    assert(stream.splitInclusive(_ % 33 == 0).toList == List(
      Chunk(1,2,3,4,5,6,7,8)
    ))
  }

  it should "handle no splits with big chunks" in {
    val stream = chunkedAscendingStream(8)
    assert(stream.splitInclusive(_ % 33 == 0).toList == List(
      Chunk(1,2,3,4,5,6,7,8)
    ))
  }

  it should "split a pre-chunked stream" in {
    val stream = chunkedAscendingStream(2,3,3)
    assert(stream.splitInclusive(_ % 3 == 0).toList == List(
      Chunk(1,2),
      Chunk(3,4,5),
      Chunk(6,7,8)
    ))
  }

  it should "split an un-chunked stream" in {
    val stream = chunkedAscendingStream(8)
    assert(stream.splitInclusive(_ % 3 == 0).toList == List(
      Chunk(1,2),
      Chunk(3,4,5),
      Chunk(6,7,8)
    ))
  }
}

package mediasort.classify

import mediasort.Spec

class MimeTypesSpec extends Spec {
  List("txt", "xml", "mp3", "wav", "mp4").foreach(e =>
    it should s"detect mime type for $e" in {
      val res = MimeType(s"a.$e")
      assert(res != "application/octet-stream")
      assert(res.contains("/"))
    }
  )
}

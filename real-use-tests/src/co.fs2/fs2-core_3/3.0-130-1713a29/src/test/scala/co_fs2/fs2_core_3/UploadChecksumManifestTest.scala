/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import scala.concurrent.duration.*

/** Exercises FS2 ordered parallel effect evaluation to build an uploaded-document checksum manifest. */
class UploadChecksumManifestTest {
  private case class Upload(name: String, content: String)
  private case class ManifestEntry(name: String, size: Int, sha256: String)

  @Test
  @Timeout(60)
  def fingerprintsDocumentsInParallelWhilePreservingSubmissionOrder(): Unit = {
    val uploads: Vector[Upload] = Vector(
      Upload("invoice-100.txt", "invoice=INV-100\ntotal=1250\n"),
      Upload("customer.txt", "customer=North Shop\n"),
      Upload("invoice-101.txt", "invoice=INV-101\ntotal=4700\n"),
      Upload("empty-attachment.txt", ""),
      Upload("invoice-100-copy.txt", "invoice=INV-100\ntotal=1250\n")
    )

    val manifest: Vector[ManifestEntry] = Stream
      .emits(uploads)
      .covary[IO]
      .parEvalMap(3) { upload =>
        IO.blocking {
          val bytes: Array[Byte] = upload.content.getBytes(StandardCharsets.UTF_8)
          val digest: Array[Byte] = MessageDigest.getInstance("SHA-256").digest(bytes)
          ManifestEntry(upload.name, bytes.length, HexFormat.of().formatHex(digest))
        }
      }
      .compile
      .toVector
      .timeout(20.seconds)
      .unsafeRunSync()

    val expected: Vector[ManifestEntry] = Vector(
      ManifestEntry("invoice-100.txt", 27,
        "c750ff5608beafc9df0863e15227dc6ba0761dae5435113414e9013247dea506"),
      ManifestEntry("customer.txt", 20,
        "eae3fec7664e9911ddd01f63bcdc671354f85883c98a0854a050336ac78a9e00"),
      ManifestEntry("invoice-101.txt", 27,
        "50db6de3d7d63093421049bf6516870a8daea1f00b7204b5112fcbba90b388b2"),
      ManifestEntry("empty-attachment.txt", 0,
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
      ManifestEntry("invoice-100-copy.txt", 27,
        "c750ff5608beafc9df0863e15227dc6ba0761dae5435113414e9013247dea506")
    )
    assertEquals(expected, manifest)
    assertEquals(uploads.map(_.name), manifest.map(_.name))
  }
}

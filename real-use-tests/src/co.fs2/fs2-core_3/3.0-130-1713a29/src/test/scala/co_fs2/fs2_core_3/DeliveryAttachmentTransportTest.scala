/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.{Chunk, Stream, text}
import org.junit.jupiter.api.Assertions.{assertArrayEquals, assertEquals}
import org.junit.jupiter.api.{Test, Timeout}

import java.util.Base64
import scala.concurrent.duration.*

/** Exercises FS2 streaming Base64 codecs to transport a binary proof-of-delivery attachment. */
class DeliveryAttachmentTransportTest {
  @Test
  @Timeout(60)
  def transportsBinaryContentAcrossByteAndBase64FragmentBoundaries(): Unit = {
    // An octet-stream attachment includes zero bytes and bytes outside the ASCII range.
    val attachment: Array[Byte] = ((0 to 255).map(_.toByte) ++
      Vector[Byte](0, 10, 13, 0)).toArray

    val outbound: Stream[IO, Byte] = Stream
      .emits(attachment.grouped(7).toVector)
      .covary[IO]
      .flatMap(bytes => Stream.chunk(Chunk.array(bytes)))

    val encoded: String = outbound
      .through(text.base64.encode)
      .compile
      .string
      .timeout(20.seconds)
      .unsafeRunSync()

    // Match the standard wire format, not just a round trip through the same pair of codecs.
    assertEquals(Base64.getEncoder.encodeToString(attachment), encoded)
    assertArrayEquals(attachment, Base64.getDecoder.decode(encoded))

    // Transport fragments need not align with the four-character Base64 encoding units.
    val received: Array[Byte] = Stream
      .emits(encoded.grouped(5).toVector)
      .covary[IO]
      .through(text.base64.decode)
      .compile
      .toVector
      .map(_.toArray)
      .timeout(20.seconds)
      .unsafeRunSync()

    assertEquals(attachment.length, received.length)
    assertArrayEquals(attachment, received)
  }
}

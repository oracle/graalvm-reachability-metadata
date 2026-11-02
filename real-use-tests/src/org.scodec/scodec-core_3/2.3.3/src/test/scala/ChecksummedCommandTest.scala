/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package scodec_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.{ChecksumFactory, fixedSizeSignature, uint8, utf8, variableSizeBytes}

/** Exercises checksummed command envelopes before an application dispatches them. */
class ChecksummedCommandTest {
  private val commandCodec: Codec[String] =
    fixedSizeSignature(4)(variableSizeBytes(uint8, utf8), ChecksumFactory.crc32)

  @Test
  @Timeout(60)
  def roundTripsACommandWithItsIntegritySignature(): Unit = {
    val command: String = "door=open"
    val encoded: ByteVector = commandCodec.encode(command).require.toByteVector

    assertEquals(command.length + 1 + 4, encoded.size)
    val decoded = commandCodec.decode(encoded.toBitVector)
    assertTrue(decoded.isSuccessful, decoded.toString)
    assertEquals(command, decoded.require.value)
  }

  @Test
  @Timeout(60)
  def rejectsACommandWithACorruptedIntegritySignature(): Unit = {
    val encoded: ByteVector = commandCodec.encode("door=closed").require.toByteVector
    val corrupted: ByteVector =
      encoded.dropRight(4) ++ ByteVector.fromValidHex("00000000")

    assertFalse(commandCodec.decode(corrupted.toBitVector).isSuccessful)
  }
}

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
import scodec.codecs.bigint

/** Exercises arbitrary-precision codecs for large signed sequence numbers. */
class LargeSequenceNumberCodecTest {
  private val sequenceNumberCodec: Codec[BigInt] = bigint(40)

  @Test
  @Timeout(60)
  def roundTripsASequenceNumberLargerThanAStandardInteger(): Unit = {
    val sequenceNumber: BigInt = BigInt("123456789")
    val encoded: ByteVector = sequenceNumberCodec.encode(sequenceNumber).require.toByteVector

    assertEquals(5L, encoded.size)
    val decoded = sequenceNumberCodec.decode(encoded.toBitVector).require

    assertEquals(sequenceNumber, decoded.value)
    assertTrue(decoded.remainder.isEmpty)
  }

  @Test
  @Timeout(60)
  def rejectsASequenceNumberOutsideTheSignedWireRange(): Unit = {
    val firstOutOfRangeValue: BigInt = BigInt(1) << 39

    assertFalse(sequenceNumberCodec.encode(firstOutOfRangeValue).isSuccessful)
  }
}

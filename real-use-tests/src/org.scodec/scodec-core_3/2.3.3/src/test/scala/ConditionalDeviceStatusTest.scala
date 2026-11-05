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
import scodec.codecs.{conditional, uint16}

/** Exercises configuration-dependent fields in a device status protocol. */
class ConditionalDeviceStatusTest {
  private val extendedStatusCodec: Codec[Option[Int]] = conditional(true, uint16)
  private val compactStatusCodec: Codec[Option[Int]] = conditional(false, uint16)

  @Test
  @Timeout(60)
  def encodesAnExtendedStatusOnlyWhenTheClientEnablesIt(): Unit = {
    val encoded: ByteVector = extendedStatusCodec.encode(Some(0x1234)).require.toByteVector
    val decoded = extendedStatusCodec.decode(encoded.toBitVector).require

    assertEquals("1234", encoded.toHex)
    assertEquals(Some(0x1234), decoded.value)
    assertTrue(decoded.remainder.isEmpty)
  }

  @Test
  @Timeout(60)
  def leavesACompactStatusWithoutTheOptionalField(): Unit = {
    val encoded: ByteVector = compactStatusCodec.encode(None).require.toByteVector
    val decoded = compactStatusCodec.decode(ByteVector.fromValidHex("aabb").toBitVector).require

    assertTrue(encoded.isEmpty)
    assertEquals(None, decoded.value)
    assertEquals("aabb", decoded.remainder.toByteVector.toHex)
  }

  @Test
  @Timeout(60)
  def rejectsATruncatedEnabledStatusField(): Unit = {
    val truncated: ByteVector = ByteVector.fromValidHex("12")

    assertFalse(extendedStatusCodec.decode(truncated.toBitVector).isSuccessful)
  }
}

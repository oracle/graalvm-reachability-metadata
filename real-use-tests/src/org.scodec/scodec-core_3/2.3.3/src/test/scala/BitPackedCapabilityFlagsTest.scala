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
import scodec.codecs.{bool, uint, uint4}

/** Exercises bit-packed capability flags in a device radio protocol. */
class BitPackedCapabilityFlagsTest {
  private case class CapabilityFlags(priority: Int, enabled: Boolean, channel: Int)

  private val flagsCodec: Codec[CapabilityFlags] = {
    val wireCodec: Codec[(Int, Boolean, Int)] = uint4 :: bool :: uint(3)
    wireCodec.xmap(
      { case (priority, enabled, channel) => CapabilityFlags(priority, enabled, channel) },
      flags => (flags.priority, flags.enabled, flags.channel)
    )
  }

  @Test
  @Timeout(60)
  def packsAndUnpacksRadioCapabilitiesIntoOneByte(): Unit = {
    val flags: CapabilityFlags = CapabilityFlags(priority = 10, enabled = true, channel = 5)
    val encoded: ByteVector = flagsCodec.encode(flags).require.toByteVector

    assertEquals("ad", encoded.toHex)
    assertEquals(flags, flagsCodec.decode(encoded.toBitVector).require.value)
  }

  @Test
  @Timeout(60)
  def rejectsAChannelValueThatDoesNotFitItsBitField(): Unit = {
    val invalidFlags: CapabilityFlags = CapabilityFlags(priority = 10, enabled = true, channel = 8)

    assertFalse(flagsCodec.encode(invalidFlags).isSuccessful)
    assertTrue(flagsCodec.decode(ByteVector.fromValidHex("ac").toBitVector).isSuccessful)
  }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package scodec_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse}
import org.junit.jupiter.api.{Test, Timeout}
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.{mappedEnum, uint8}

/** Exercises mapped-enum codecs for device operating modes in a control client. */
class DeviceModeCodecTest {
  private val modeCodec: Codec[String] =
    mappedEnum(uint8, Map("idle" -> 1, "ready" -> 2, "maintenance" -> 3))

  @Test
  @Timeout(60)
  def encodesAndDecodesTheModeReportedByADevice(): Unit = {
    val mode: String = "ready"
    val encoded: ByteVector = modeCodec.encode(mode).require.toByteVector

    assertEquals("02", encoded.toHex)
    assertEquals(mode, modeCodec.decode(encoded.toBitVector).require.value)
  }

  @Test
  @Timeout(60)
  def rejectsAnUnknownModeCodeFromADevice(): Unit = {
    val unknownMode: ByteVector = ByteVector.fromValidHex("04")

    assertFalse(modeCodec.decode(unknownMode.toBitVector).isSuccessful)
  }
}

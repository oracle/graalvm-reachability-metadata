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
import scodec.codecs.{uint8, variableSizeBytes}

/** Exercises binary protocol framing for a telemetry message with a bounded payload. */
class TelemetryFrameCodecTest {
  private case class TelemetryFrame(version: Int, device: String, readings: String)

  private val frameCodec: Codec[TelemetryFrame] = {
    val wireCodec: Codec[(Int, String, String)] =
      uint8 :: variableSizeBytes(uint8, scodec.codecs.utf8) :: scodec.codecs.utf8
    wireCodec.xmap(
      { case (version, device, readings) => TelemetryFrame(version, device, readings) },
      frame => (frame.version, frame.device, frame.readings)
    )
  }

  @Test
  @Timeout(60)
  def encodesAndDecodesAnApplicationTelemetryFrame(): Unit = {
    val frame: TelemetryFrame = TelemetryFrame(2, "weather-station-7", "temperature=21.5;humidity=48")
    val encoded: ByteVector = frameCodec.encode(frame).require.toByteVector

    assertEquals(
      "0211776561746865722d73746174696f6e2d3774656d70657261747572653d32312e353b68756d69646974793d3438",
      encoded.toHex
    )
    val decoded = frameCodec.decode(encoded.toBitVector).require
    assertEquals(frame, decoded.value)
    assertTrue(decoded.remainder.isEmpty)
  }

  @Test
  @Timeout(60)
  def rejectsAFrameWhosePayloadIsShorterThanItsLengthPrefix(): Unit = {
    val truncated: ByteVector = ByteVector.fromValidHex("021077656174686572")
    val decoded = frameCodec.decode(truncated.toBitVector)

    assertFalse(decoded.isSuccessful)
  }
}

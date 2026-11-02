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
import scodec.codecs.{bool, optional, uint8}

/** Exercises optional device status fields in a compact device protocol. */
class OptionalDeviceStatusCodecTest {
  private case class DeviceStatus(deviceId: Int, batteryPercent: Option[Int])

  private val statusCodec: Codec[DeviceStatus] = {
    val wireCodec: Codec[(Int, Option[Int])] = uint8 :: optional(bool(8), uint8)
    wireCodec.xmap(
      { case (deviceId, batteryPercent) => DeviceStatus(deviceId, batteryPercent) },
      status => (status.deviceId, status.batteryPercent)
    )
  }

  @Test
  @Timeout(60)
  def roundTripsStatusesWithAndWithoutAnOptionalBatteryReading(): Unit = {
    val knownBattery: DeviceStatus = DeviceStatus(42, Some(87))
    val unavailableBattery: DeviceStatus = DeviceStatus(42, None)

    val encodedKnown: ByteVector = statusCodec.encode(knownBattery).require.toByteVector
    val encodedUnavailable: ByteVector = statusCodec.encode(unavailableBattery).require.toByteVector

    assertEquals("2aff57", encodedKnown.toHex)
    assertEquals("2a00", encodedUnavailable.toHex)
    assertEquals(knownBattery, statusCodec.decode(encodedKnown.toBitVector).require.value)
    assertEquals(unavailableBattery, statusCodec.decode(encodedUnavailable.toBitVector).require.value)
  }

  @Test
  @Timeout(60)
  def rejectsAnOptionalBatteryReadingWithNoPayload(): Unit = {
    val truncated: ByteVector = ByteVector.fromValidHex("2a01")

    assertFalse(statusCodec.decode(truncated.toBitVector).isSuccessful)
  }
}

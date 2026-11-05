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
import scodec.codecs.{bytes, fixedSizeBytes, ignore, paddedFixedSizeBytes, uint8, uint16}

/** Exercises fixed-size padded sensor records in a device storage protocol. */
class PaddedSensorRecordCodecTest {
  private case class SensorRecord(sensorId: Int, reading: Int)

  private val recordCodec: Codec[SensorRecord] = {
    val wireCodec: Codec[(Int, Int)] = uint16 :: uint8
    wireCodec.xmap(
      { case (sensorId, reading) => SensorRecord(sensorId, reading) },
      record => (record.sensorId, record.reading)
    )
  }

  private val storageCodec: Codec[SensorRecord] =
    paddedFixedSizeBytes(8, recordCodec, ignore(8))

  @Test
  @Timeout(60)
  def storesARecordInAStableEightByteSlot(): Unit = {
    val record: SensorRecord = SensorRecord(0x1234, 87)
    val followingSlot: ByteVector = ByteVector.fromValidHex("aabb")
    val encoded: ByteVector = storageCodec.encode(record).require.toByteVector
    val decoded = storageCodec.decode((encoded ++ followingSlot).toBitVector).require

    assertEquals("1234570000000000", encoded.toHex)
    assertEquals(record, decoded.value)
    assertEquals(followingSlot, decoded.remainder.toByteVector)
  }

  @Test
  @Timeout(60)
  def rejectsARecordThatCannotFitInsideItsStorageSlot(): Unit = {
    val oversizedRecord: Codec[ByteVector] =
      paddedFixedSizeBytes(2, fixedSizeBytes(3, bytes), ignore(8))
    val payload: ByteVector = ByteVector.fromValidHex("010203")

    assertFalse(oversizedRecord.encode(payload).isSuccessful)
  }
}

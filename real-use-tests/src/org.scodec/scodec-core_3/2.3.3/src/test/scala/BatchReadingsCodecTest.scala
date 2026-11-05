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
import scodec.codecs.{uint8, vectorOfN, vint, zint}

/** Exercises length-prefixed batches of signed sensor readings for a telemetry client. */
class BatchReadingsCodecTest {
  private case class Reading(sensorId: Int, value: Int)

  private val readingCodec: Codec[Reading] = {
    val wireCodec: Codec[(Int, Int)] = uint8 :: zint
    wireCodec.xmap(
      { case (sensorId, value) => Reading(sensorId, value) },
      reading => (reading.sensorId, reading.value)
    )
  }

  private val batchCodec: Codec[Vector[Reading]] = vectorOfN(vint, readingCodec)

  @Test
  @Timeout(60)
  def encodesACompactBatchWithItsElementCount(): Unit = {
    val readings: Vector[Reading] = Vector(Reading(7, -3), Reading(8, 21), Reading(9, 0))
    val encoded: ByteVector = batchCodec.encode(readings).require.toByteVector

    assertEquals("0307 0508 2a09 00".replace(" ", ""), encoded.toHex)
    assertEquals(readings, batchCodec.decode(encoded.toBitVector).require.value)
  }

  @Test
  @Timeout(60)
  def rejectsABatchWhoseElementCountExceedsItsPayload(): Unit = {
    val truncated: ByteVector = ByteVector.fromValidHex("030705")

    assertFalse(batchCodec.decode(truncated.toBitVector).isSuccessful)
  }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package scodec_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNotEquals}
import org.junit.jupiter.api.{Test, Timeout}
import scodec.Codec
import scodec.bits.{BitVector, ByteVector}
import scodec.codecs.{uint8, utf8, variableSizeBytes, zlib}

/** Exercises compressed telemetry payloads exchanged by a bandwidth-constrained client. */
class CompressedTelemetryPayloadTest {
  private val payloadCodec: Codec[String] =
    zlib(variableSizeBytes(uint8, utf8), chunkSize = 32)

  @Test
  @Timeout(60)
  def compressesAndRestoresARepetitiveTelemetryPayload(): Unit = {
    val payload: String = "temperature=21.5;temperature=21.5;temperature=21.5"
    val encoded: BitVector = payloadCodec.encode(payload).require

    assertNotEquals(payload.getBytes("UTF-8").length, encoded.bytes.size)
    assertEquals(payload, payloadCodec.decode(encoded).require.value)
  }

  @Test
  @Timeout(60)
  def rejectsACompressedPayloadWithCorruptedBytes(): Unit = {
    val encoded: BitVector = payloadCodec.encode("pressure=1013").require
    val corrupted: BitVector = encoded.take(8) ++ ByteVector(0x00, 0x00, 0x00, 0x00).bits

    assertFalse(payloadCodec.decode(corrupted).isSuccessful)
  }
}

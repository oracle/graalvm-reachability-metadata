/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package scodec_core_3

import java.util.UUID

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.uuid

/** Exercises UUID encoding for a device session identifier. */
class SessionIdentifierCodecTest {
  private val sessionIdentifierCodec: Codec[UUID] = uuid

  @Test
  @Timeout(60)
  def encodesAndDecodesADeviceSessionIdentifier(): Unit = {
    val sessionIdentifier: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    val encoded: ByteVector = sessionIdentifierCodec.encode(sessionIdentifier).require.toByteVector

    assertEquals("123e4567e89b12d3a456426614174000", encoded.toHex)
    val decoded = sessionIdentifierCodec.decode(encoded.toBitVector).require
    assertEquals(sessionIdentifier, decoded.value)
    assertTrue(decoded.remainder.isEmpty)
  }

  @Test
  @Timeout(60)
  def rejectsASessionIdentifierWithTheWrongSize(): Unit = {
    val truncated: ByteVector = ByteVector.fromValidHex("123e4567e89b12d3a45642661417")

    assertFalse(sessionIdentifierCodec.decode(truncated.toBitVector).isSuccessful)
  }
}

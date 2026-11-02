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
import scodec.codecs.{constant, fallback, uint8, utf8, variableSizeBytes}

/** Exercises fallback decoding for a client that accepts legacy and current responses. */
class VersionedResponseFallbackTest {
  private case class LegacyResponse(message: String)
  private case class CurrentResponse(message: String)

  private val legacyCodec: Codec[LegacyResponse] = {
    val wireCodec: Codec[(Unit, String)] = constant(ByteVector.fromValidHex("01")) :: variableSizeBytes(uint8, utf8)
    wireCodec.xmap(
      { case (_, message) => LegacyResponse(message) },
      response => ((), response.message)
    )
  }

  private val currentCodec: Codec[CurrentResponse] = {
    val wireCodec: Codec[(Unit, String)] = constant(ByteVector.fromValidHex("02")) :: variableSizeBytes(uint8, utf8)
    wireCodec.xmap(
      { case (_, message) => CurrentResponse(message) },
      response => ((), response.message)
    )
  }

  private val responseCodec: Codec[Either[LegacyResponse, CurrentResponse]] =
    fallback(legacyCodec, currentCodec)

  @Test
  @Timeout(60)
  def decodesBothResponseVersionsAndPreservesTheirPayloads(): Unit = {
    val legacy: Either[LegacyResponse, CurrentResponse] = Left(LegacyResponse("queued"))
    val current: Either[LegacyResponse, CurrentResponse] = Right(CurrentResponse("accepted"))

    assertEquals("0106717565756564", responseCodec.encode(legacy).require.toHex)
    assertEquals("02086163636570746564", responseCodec.encode(current).require.toHex)
    assertEquals(legacy, responseCodec.decode(ByteVector.fromValidHex("0106717565756564").toBitVector).require.value)
    assertEquals(current, responseCodec.decode(ByteVector.fromValidHex("02086163636570746564").toBitVector).require.value)
  }

  @Test
  @Timeout(60)
  def rejectsAResponseWithAnUnsupportedVersionTag(): Unit = {
    val unsupported: ByteVector = ByteVector.fromValidHex("03046f6f7073")

    val decoded = responseCodec.decode(unsupported.toBitVector)

    assertFalse(decoded.isSuccessful)
  }
}

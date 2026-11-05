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
import scodec.codecs.{constant, utf8, variableSizeDelimited}

/** Exercises delimiter-terminated command frames in a device control stream. */
class DelimitedCommandCodecTest {
  private val commandCodec: Codec[String] =
    variableSizeDelimited(constant(ByteVector.fromValidHex("00")), utf8, 8L)

  @Test
  @Timeout(60)
  def encodesAndDecodesACommandBeforeTheNextStreamFrame(): Unit = {
    val command: String = "set-mode=safe"
    val followingCommand: ByteVector = ByteVector.fromValidHex("726573657400")
    val encoded: ByteVector = commandCodec.encode(command).require.toByteVector
    val stream: ByteVector = encoded ++ followingCommand
    val decoded = commandCodec.decode(stream.toBitVector).require

    assertEquals("7365742d6d6f64653d7361666500", encoded.toHex)
    assertEquals(command, decoded.value)
    assertEquals(followingCommand, decoded.remainder.toByteVector)
  }

  @Test
  @Timeout(60)
  def rejectsACommandStreamWithoutItsDelimiter(): Unit = {
    val unterminated: ByteVector = ByteVector.fromValidHex("7365742d6d6f64653d73616665")

    assertFalse(commandCodec.decode(unterminated.toBitVector).isSuccessful)
  }
}

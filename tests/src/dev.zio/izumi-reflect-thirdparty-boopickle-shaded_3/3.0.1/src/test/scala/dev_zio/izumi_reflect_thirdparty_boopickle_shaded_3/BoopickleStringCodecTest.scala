/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package izumi.reflect

import _root_.izumi.reflect.thirdparty.internal.boopickle.StringCodec
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoopickleStringCodecTest {
  @Test
  def utf8CodecRoundTripsEmptyAsciiAndMultibyteStrings(): Unit = {
    val values: List[String] = List(
      "",
      "boopickle shaded codec",
      "accented café, greek λ, cyrillic Ж, emoji 🚀"
    )

    values.foreach { value =>
      val encoded: ByteBuffer = StringCodec.encodeUTF8(value)
      assertEquals(value.getBytes(StandardCharsets.UTF_8).length, encoded.remaining)

      val decoded: String = StringCodec.decodeUTF8(encoded.remaining, encoded.duplicate)
      assertEquals(value, decoded)
    }
  }

  @Test
  def utf16CodecUsesLittleEndianEncodingAndRoundTripsSupplementaryCharacters(): Unit = {
    val value: String = "A𐐷Z漢字"
    val encoded: ByteBuffer = StringCodec.encodeUTF16(value)
    val expectedBytes: Array[Byte] = value.getBytes(StandardCharsets.UTF_16LE)

    assertEquals(expectedBytes.length, encoded.remaining)
    expectedBytes.indices.foreach { index =>
      assertEquals(expectedBytes(index), encoded.get(index))
    }

    val decoded: String = StringCodec.decodeUTF16(encoded.remaining, encoded.duplicate)
    assertEquals(value, decoded)
  }

  @Test
  def decodeConsumesOnlyTheRequestedSliceAndAdvancesBufferPosition(): Unit = {
    val first: ByteBuffer = StringCodec.encodeUTF8("first")
    val second: ByteBuffer = StringCodec.encodeUTF8("second")
    val combined: ByteBuffer = ByteBuffer.allocate(first.remaining + second.remaining)
    combined.put(first.duplicate)
    combined.put(second.duplicate)
    combined.flip()

    val decodedFirst: String = StringCodec.decodeUTF8("first".getBytes(StandardCharsets.UTF_8).length, combined)
    assertEquals("first", decodedFirst)
    assertEquals(second.remaining, combined.remaining)
    assertFalse(combined.position == 0)

    val decodedSecond: String = StringCodec.decodeUTF8(combined.remaining, combined)
    assertEquals("second", decodedSecond)
    assertFalse(combined.hasRemaining)
  }

  @Test
  def encodedBuffersAreReadableFromStartAndAreIndependentForEachCall(): Unit = {
    val left: ByteBuffer = StringCodec.encodeUTF8("same")
    val right: ByteBuffer = StringCodec.encodeUTF8("same")

    assertEquals(0, left.position)
    assertEquals(0, right.position)
    assertEquals(left.remaining, right.remaining)
    assertTrue(left ne right)

    left.get()
    assertEquals(1, left.position)
    assertEquals(0, right.position)
  }
}

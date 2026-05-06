/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scodec.scodec_bits_3

import org.junit.jupiter.api.Assertions.{assertArrayEquals, assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import scodec.bits.{BitVector, ByteVector, HexDumpFormat}

class Scodec_bits_3Test:

  @Test
  def byteVectorsPreserveEncodingsAndBitwiseOperations(): Unit =
    val bytes: ByteVector = ByteVector.fromValidHex("cafe00ff")
    val mask: ByteVector = ByteVector.fromValidHex("0ff0330f")

    assertEquals("cafe00ff", bytes.toHex)
    assertEquals(bytes, ByteVector.fromValidBase64(bytes.toBase64))
    assertEquals("Cafe", ByteVector.fromValidHex("43616665").decodeUtf8.toOption.get)
    assertArrayEquals(Array[Byte](0xCA.toByte, 0xFE.toByte, 0x00.toByte, 0xFF.toByte), bytes.toArray)
    assertEquals("c50e33f0", bytes.xor(mask).toHex)
    assertTrue(bytes.startsWith(ByteVector.fromValidHex("cafe")))

  @Test
  def bitVectorsSupportBitLevelSlicingAndConcatenation(): Unit =
    val bits: BitVector = BitVector.fromValidBin("101011110001")
    val prefix: BitVector = bits.take(4)
    val suffix: BitVector = bits.drop(4)
    val rebuilt: BitVector = prefix ++ suffix

    assertEquals("101011110001", bits.toBin)
    assertEquals("1010", prefix.toBin)
    assertEquals(bits, rebuilt)
    assertEquals(bits, BitVector(bits.toByteVector).take(bits.size))

  @Test
  def hexDumpFormattingRendersStableStructuredOutput(): Unit =
    val bytes: ByteVector = ByteVector.fromValidHex("00010203040506070809")
    val format: HexDumpFormat = HexDumpFormat.NoAnsi
      .withIncludeAsciiColumn(false)
      .withDataColumnCount(1)
      .withDataColumnWidthInBytes(4)
    val rendered: String = format.render(bytes)

    assertTrue(rendered.contains("00000000"))
    assertTrue(rendered.contains("00 01 02 03"))
    assertTrue(rendered.contains("08 09"))

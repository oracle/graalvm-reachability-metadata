/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scodec.scodec_bits_3

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.channels.Channels
import java.util.UUID

import org.junit.jupiter.api.Assertions.{assertArrayEquals, assertEquals, assertFalse, assertThrows, assertTrue}
import org.junit.jupiter.api.Test
import scodec.bits.{Bases, BitVector, ByteOrdering, ByteVector, crc}

class Scodec_bits_3Test {
  @Test
  def byteVectorSupportsCollectionStyleEditingAndQueries(): Unit = {
    val bytes: ByteVector = ByteVector(1, 2, 3, 4, 5)

    assertEquals(5L, bytes.size)
    assertEquals(5L, bytes.length)
    assertFalse(bytes.isEmpty)
    assertTrue(bytes.nonEmpty)
    assertEquals(1.toByte, bytes(0L))
    assertEquals(Some(3.toByte), bytes.lift(2L))
    assertEquals(None, bytes.lift(-1L))
    assertEquals("0102030405", bytes.toHex)
    assertEquals("0102030405", ByteVector.concat(List(ByteVector(1, 2), ByteVector(3), ByteVector(4, 5))).toHex)

    assertEquals("0102090405", bytes.update(2L, 9.toByte).toHex)
    assertEquals("010209030405", bytes.insert(2L, 9.toByte).toHex)
    assertEquals("01020908030405", bytes.splice(2L, ByteVector(9, 8)).toHex)
    assertEquals("010a0b0c05", bytes.patch(1L, ByteVector(0x0a, 0x0b, 0x0c)).toHex)
    assertEquals("010203", bytes.take(3L).toHex)
    assertEquals("0405", bytes.drop(3L).toHex)
    assertEquals("020304", bytes.slice(1L, 4L).toHex)
    assertEquals("030405", bytes.takeRight(3L).toHex)
    assertEquals("0102", bytes.dropRight(3L).toHex)
    assertEquals("0504030201", bytes.reverse.toHex)

    assertTrue(bytes.startsWith(ByteVector(1, 2)))
    assertTrue(bytes.endsWith(ByteVector(4, 5)))
    assertTrue(bytes.containsSlice(ByteVector(3, 4)))
    assertEquals(2L, bytes.indexOfSlice(ByteVector(3, 4)))
    assertEquals(List("0102", "0304", "05"), bytes.grouped(2L).map(_.toHex).toList)
    assertEquals("0203040506", bytes.map(b => (b + 1).toByte).toHex)
    assertEquals("fefdfcfbfa", bytes.xor(ByteVector.high(5L)).toHex)

    val copiedToStream: ByteArrayOutputStream = new ByteArrayOutputStream()
    bytes.copyToStream(copiedToStream)
    assertArrayEquals(Array[Byte](1, 2, 3, 4, 5), copiedToStream.toByteArray)

    val destination: ByteBuffer = ByteBuffer.allocate(3)
    assertEquals(3, bytes.copyToBuffer(destination))
    assertArrayEquals(Array[Byte](1, 2, 3), destination.array())

    assertThrows(classOf[IndexOutOfBoundsException], () => {
      bytes(5L)
      ()
    })
  }

  @Test
  def byteVectorDistinguishesCopiesFromViewsAndBuffers(): Unit = {
    val backing: Array[Byte] = Array[Byte](1, 2, 3, 4)
    val copied: ByteVector = ByteVector(backing)
    val viewed: ByteVector = ByteVector.view(backing)
    backing(0) = 9.toByte

    assertEquals("01020304", copied.toHex)
    assertEquals("09020304", viewed.toHex)
    assertArrayEquals(Array[Byte](1, 2, 3, 4), copied.toArray)

    val sourceBuffer: ByteBuffer = ByteBuffer.wrap(Array[Byte](10, 11, 12, 13))
    sourceBuffer.position(1)
    sourceBuffer.limit(3)
    val copiedFromBuffer: ByteVector = ByteVector(sourceBuffer)
    val viewedFromBuffer: ByteVector = ByteVector.view(sourceBuffer)

    assertEquals("0b0c", copiedFromBuffer.toHex)
    assertEquals("0b0c", viewedFromBuffer.toHex)
    assertTrue(copiedFromBuffer.toByteBuffer.isReadOnly)
    assertEquals("0b0c", copiedFromBuffer.toBitVector.toHex)

    val generated: ByteVector = ByteVector.viewI(i => (i * 3 + 1).toInt, 4L)
    assertEquals("0104070a", generated.toHex)
    val buffered: ByteVector = (ByteVector(1, 2).buffer :+ 3.toByte) ++ ByteVector(4, 5, 6)
    assertEquals("010203040506", buffered.unbuffer.toHex)
    assertEquals("000000", ByteVector.low(3L).toHex)
    assertEquals("ffffff", ByteVector.high(3L).toHex)
  }

  @Test
  def byteVectorEncodesNumbersStringsUuidsAndBases(): Unit = {
    val uuid: UUID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")
    val text: String = "scodec bits \u2713"
    val utf8: ByteVector = expectRight(ByteVector.encodeUtf8(text))
    val ascii: ByteVector = expectRight(ByteVector.encodeAscii("ASCII"))

    assertEquals("01020304", ByteVector.fromInt(0x01020304).toHex)
    assertEquals("04030201", ByteVector.fromInt(0x01020304, ordering = ByteOrdering.LittleEndian).toHex)
    assertEquals("0102030405060708", ByteVector.fromLong(0x0102030405060708L).toHex)
    assertEquals(0x01020304, ByteVector.fromInt(0x01020304).toInt(signed = false))
    assertEquals(0x01020304, ByteVector.fromInt(0x01020304, ordering = ByteOrdering.LittleEndian).toInt(signed = false, ordering = ByteOrdering.LittleEndian))
    assertEquals(uuid, ByteVector.fromUUID(uuid).toUUID)
    assertEquals(ByteOrdering.BigEndian, ByteOrdering.fromJava(ByteOrder.BIG_ENDIAN))
    assertEquals(ByteOrdering.LittleEndian, ByteOrdering.fromJava(ByteOrder.LITTLE_ENDIAN))

    assertEquals(text, expectRight(utf8.decodeUtf8))
    assertEquals("ASCII", expectRight(ascii.decodeAscii))
    assertEquals(utf8, ByteVector.fromValidHex("0x" + utf8.toHex))
    assertEquals(utf8, ByteVector.fromValidBin(utf8.toBin))
    assertEquals(utf8, ByteVector.fromValidBase32(utf8.toBase32))
    assertEquals(utf8, ByteVector.fromValidBase58(utf8.toBase58))
    assertEquals(utf8, ByteVector.fromValidBase64(utf8.toBase64))
    assertEquals(utf8, ByteVector.fromValidBase64(utf8.toBase64NoPad, Bases.Alphabets.Base64NoPad))
    assertEquals(utf8, ByteVector.fromValidBase64(utf8.toBase64Url, Bases.Alphabets.Base64Url))
    assertEquals(utf8, ByteVector.fromValidBase64(utf8.toBase64UrlNoPad, Bases.Alphabets.Base64UrlNoPad))
    assertEquals(utf8.toHex.toUpperCase, utf8.toHex(Bases.Alphabets.HexUppercase))

    assertEquals(None, ByteVector.fromHex("not hex"))
    assertTrue(ByteVector.fromHexDescriptive("01xz").isLeft)
    assertTrue(ByteVector.fromBase64Descriptive("%%%%").isLeft)
  }

  @Test
  def bitVectorSupportsBitLevelConstructionTransformsAndParsing(): Unit = {
    val bits: BitVector = BitVector.fromValidBin("0b101_0011")

    assertEquals(7L, bits.size)
    assertEquals("1010011", bits.toBin)
    assertEquals("a6", bits.toHex)
    assertEquals(4L, bits.populationCount)
    assertTrue(bits.startsWith(BitVector.fromValidBin("101")))
    assertTrue(bits.endsWith(BitVector.fromValidBin("011")))
    assertEquals(2L, bits.indexOfSlice(BitVector.fromValidBin("100")))
    assertEquals(List("101", "001", "1"), bits.grouped(3L).map(_.toBin).toList)

    assertEquals("1100011", bits.set(1L).clear(2L).toBin)
    assertEquals("10110011", bits.insert(3L, true).toBin)
    assertEquals("1000011", bits.patch(2L, BitVector.low(2L)).toBin)
    assertEquals("0001010011", bits.padLeft(10L).toBin)
    assertEquals("1010011000", bits.padRight(10L).toBin)
    assertEquals("1100101", bits.reverse.toBin)
    assertEquals("1001110", bits.rotateLeft(2L).toBin)
    assertEquals("1110100", bits.rotateRight(2L).toBin)
    assertEquals("0101100", bits.xor(BitVector.high(7L)).toBin)
    assertEquals("1010011", bits.force.toBin)

    assertEquals("10101010", BitVector.bits(List(true, false, true, false, true, false, true, false)).toBin)
    assertEquals("11111", BitVector.high(5L).toBin)
    assertEquals("00000", BitVector.low(5L).toBin)
    assertEquals("3412", BitVector.fromShort(0x1234.toShort, ordering = ByteOrdering.LittleEndian).toHex)
    assertEquals(0x1234, BitVector.fromShort(0x1234.toShort).toInt(signed = false))
    assertEquals(BitVector.fromValidHex("abc"), BitVector.fromValidBin("101010111100"))
    assertEquals(None, BitVector.fromBin("10201"))
    assertTrue(BitVector.fromBase32Descriptive("*").isLeft)
  }

  @Test
  def bitVectorReadsBoundedStreamsChannelsAndLazyUnfolds(): Unit = {
    val streamBytes: Array[Byte] = Array[Byte](1, 2, 3, 4, 5)
    val input: ByteArrayInputStream = new ByteArrayInputStream(streamBytes)
    try {
      assertEquals("0102030405", BitVector.fromInputStream(input, chunkSizeInBytes = 2).force.toHex)
    } finally {
      input.close()
    }

    val channelInput: ByteArrayInputStream = new ByteArrayInputStream(Array[Byte](6, 7, 8, 9))
    val channel = Channels.newChannel(channelInput)
    try {
      assertEquals("06070809", BitVector.fromChannel(channel, chunkSizeInBytes = 2, direct = true).force.toHex)
    } finally {
      channel.close()
      channelInput.close()
    }

    val unfolded: BitVector = BitVector.unfold(0) { index =>
      if (index >= 4) None else Some((BitVector.fromByte((index + 1).toByte), index + 1))
    }
    assertEquals("01020304", unfolded.force.toHex)
  }

  @Test
  def compressionDigestsAndCrcsRoundTripDeterministically(): Unit = {
    val payload: ByteVector = expectRight(ByteVector.encodeAscii("123456789"))
    val abc: ByteVector = expectRight(ByteVector.encodeAscii("abc"))
    val compressed: ByteVector = payload.deflate()
    val compressedBits: BitVector = payload.bits.deflate()

    assertEquals(payload, expectRight(compressed.inflate()))
    assertEquals(payload.bits, expectRight(compressedBits.inflate()))
    assertEquals(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
      abc.digest("SHA-256").toHex
    )
    assertEquals("cbf43926", crc.crc32(payload.bits).toHex)
    assertEquals("e3069283", crc.crc32c(payload.bits).toHex)
  }

  private def expectRight[L, R](either: Either[L, R]): R =
    either match {
      case Right(value) => value
      case Left(error)  => throw new AssertionError(s"Expected Right, got Left($error)")
    }
}

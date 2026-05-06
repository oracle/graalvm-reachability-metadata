/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scodec.scodec_bits_3

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.ThrowableAssert.ThrowingCallable
import org.junit.jupiter.api.Test
import scodec.bits.*

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.jdk.CollectionConverters.*

final class Scodec_bits_3Test {
  @Test
  def byteVectorSupportsPersistentCollectionOperationsAndViews(): Unit = {
    val copied = ByteVector(Array[Byte](0x10, 0x20, 0x30))
    val source = Array[Byte](0x01, 0x02, 0x03, 0x04)
    val viewed = ByteVector.view(source, 1, 2)
    source(1) = 0x7f.toByte

    val vector = (copied ++ viewed)
      .insert(1, 0x11.toByte)
      .patch(3, ByteVector.fromValidHex("aabb"))
      .update(0, 0x0f.toByte)

    assertThat(copied.toHex).isEqualTo("102030")
    assertThat(viewed.toHex).isEqualTo("7f03")
    assertThat(vector.toHex).isEqualTo("0f1120aabb03")
    assertThat(vector.size).isEqualTo(6L)
    assertThat(vector.headOption).isEqualTo(Some(0x0f.toByte))
    assertThat(vector.lastOption).isEqualTo(Some(0x03.toByte))
    assertThat(vector.lift(99)).isEqualTo(None)
    assertThat(vector.slice(2, 6).toHex).isEqualTo("20aabb03")
    assertThat(vector.takeWhile(_ != 0xaa.toByte).toHex).isEqualTo("0f1120")
    assertThat(vector.dropWhile(_ != 0xaa.toByte).toHex).isEqualTo("aabb03")
    assertThat(vector.indexOfSlice(ByteVector.fromValidHex("aabb"))).isEqualTo(3L)
    assertThat(vector.containsSlice(ByteVector.fromValidHex("bb03"))).isTrue()
    assertThat(vector.startsWith(ByteVector.fromValidHex("0f11"))).isTrue()
    assertThat(vector.endsWith(ByteVector.fromValidHex("bb03"))).isTrue()
    assertThat(vector.grouped(3).map(_.toHex).toList.asJava).containsExactly("0f1120", "aabb03")
    assertThat(vector.compare(ByteVector.fromValidHex("0f1120aabb037f04"))).isLessThan(0)
  }

  @Test
  def byteVectorConvertsToAndFromStreamsBuffersNumbersAndText(): Unit = {
    val uuid = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")
    val fromBufferSource = ByteBuffer.wrap(Array[Byte](9, 8, 7, 6, 5))
    fromBufferSource.position(1)
    fromBufferSource.limit(4)

    val vector = ByteVector.fromUUID(uuid) ++ ByteVector(fromBufferSource)
    val output = new ByteArrayOutputStream()
    vector.copyToStream(output)
    val copyTarget = Array.fill[Byte](6)(0.toByte)
    vector.copyToArray(copyTarget, 1, 14, 4)
    val inputStream = vector.takeRight(3).toInputStream

    try {
      assertThat(ByteVector.fromUUID(uuid).toUUID).isEqualTo(uuid)
      assertThat(ByteVector(fromBufferSource).toHex).isEqualTo("080706")
      assertThat(output.toByteArray).isEqualTo(vector.toArray)
      assertThat(copyTarget).isEqualTo(Array[Byte](0, 0xee.toByte, 0xff.toByte, 8, 7, 0))
      assertThat(inputStream.read()).isEqualTo(8)
      assertThat(inputStream.read()).isEqualTo(7)
      assertThat(inputStream.read()).isEqualTo(6)
      assertThat(inputStream.read()).isEqualTo(-1)
      assertThat(ByteVector.fromShort(0x1234.toShort).toHex).isEqualTo("1234")
      assertThat(ByteVector.fromInt(0x01020304, ordering = ByteOrdering.LittleEndian).toHex).isEqualTo("04030201")
      assertThat(ByteVector.fromLong(0x0102030405060708L, size = 6).toHex).isEqualTo("030405060708")
      assertThat(ByteVector.encodeUtf8("scodec ✓").map(_.decodeUtf8)).isEqualTo(Right(Right("scodec ✓")))
      assertThat(ByteVector.fromValidHex("ff").toInt(signed = false)).isEqualTo(255)
      assertThat(ByteVector.fromValidHex("ff").toInt(signed = true)).isEqualTo(-1)
    } finally {
      inputStream.close()
      output.close()
    }
  }

  @Test
  def byteVectorBaseEncodingsValidateRoundTripsAndFailures(): Unit = {
    val data = ByteVector.encodeAscii("hello world").getOrElse(ByteVector.empty)

    assertThat(data.toHex).isEqualTo("68656c6c6f20776f726c64")
    assertThat(ByteVector.fromValidHex("0x6865 6c6c_6f").decodeAscii).isEqualTo(Right("hello"))
    assertThat(data.toBin.take(16)).isEqualTo("0110100001100101")
    assertThat(ByteVector.fromValidBin("0b01101000_01101001").decodeAscii).isEqualTo(Right("hi"))
    assertThat(data.toBase32).isEqualTo("NBSWY3DPEB3W64TMMQ======")
    assertThat(ByteVector.fromValidBase32(data.toBase32)).isEqualTo(data)
    assertThat(data.toBase58).isEqualTo("StV1DL6CwTryKyV")
    assertThat(ByteVector.fromValidBase58(data.toBase58)).isEqualTo(data)
    assertThat(data.toBase64).isEqualTo("aGVsbG8gd29ybGQ=")
    assertThat(data.toBase64NoPad).isEqualTo("aGVsbG8gd29ybGQ")
    assertThat(data.toBase64Url).isEqualTo("aGVsbG8gd29ybGQ=")
    assertThat(ByteVector.fromValidBase64(data.toBase64)).isEqualTo(data)
    assertThat(ByteVector.fromHex("not-hex")).isEqualTo(None)
    assertThat(ByteVector.fromBase64Descriptive("****").isLeft).isTrue()
    assertThatThrownBy(new ThrowingCallable {
      override def call(): Unit = ByteVector.fromValidBin("01012")
    }).isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def byteVectorBitwiseCompressionHashingAndHexDumpAreDeterministic(): Unit = {
    val payload = ByteVector.encodeUtf8("native image friendly compression payload").getOrElse(ByteVector.empty)
    val mask = ByteVector.high(payload.size)
    val compressed = payload.deflate()

    assertThat((payload xor mask).xor(mask)).isEqualTo(payload)
    assertThat((payload and mask)).isEqualTo(payload)
    assertThat((payload or ByteVector.low(payload.size))).isEqualTo(payload)
    assertThat((~ByteVector.fromValidHex("00ff")).toHex).isEqualTo("ff00")
    assertThat(ByteVector.fromValidHex("1234").shiftLeft(4).toHex).isEqualTo("2340")
    assertThat(ByteVector.fromValidHex("1234").shiftRight(4, signExtension = false).toHex).isEqualTo("0123")
    assertThat(payload.rotateLeft(12).rotateRight(12)).isEqualTo(payload)
    assertThat(compressed.inflate()).isEqualTo(Right(payload))
    assertThat(payload.sha1.toHex).isEqualTo("d6b08e66ee45e3b11941301a6ed6aaaf9d224f7e")
    assertThat(payload.sha256.toHex).isEqualTo("774f0482bed346fe644341ea00cf3d466a7710a111e993ff5a0fbbcf70e31d50")
    assertThat(HexDumpFormat.NoAscii.withAnsi(false).withDataColumnWidthInBytes(4).render(ByteVector.fromValidHex("000102030405")))
      .isEqualTo("00000000  00 01 02 03  04 05  \n")
  }

  @Test
  def bitVectorSupportsUnalignedUpdatesSlicingAndOrdering(): Unit = {
    val bits = BitVector.fromValidBin("1011_0010") ++ BitVector.fromValidHex("f").take(3)
    val edited = bits.clear(0).set(1).insert(4, true).patch(6, BitVector.fromValidBin("000"))

    assertThat(bits.size).isEqualTo(11L)
    assertThat(bits.toBin).isEqualTo("10110010111")
    assertThat(edited.toBin).isEqualTo("011110000111")
    assertThat(edited.populationCount).isEqualTo(7L)
    assertThat(edited.head).isFalse()
    assertThat(edited.last).isTrue()
    assertThat(edited.lift(100)).isEqualTo(None)
    assertThat(edited.splitAt(5)._1.toBin).isEqualTo("01111")
    assertThat(edited.splitAt(5)._2.toBin).isEqualTo("0000111")
    assertThat(edited.indexOfSlice(BitVector.fromValidBin("000"))).isEqualTo(5L)
    assertThat(edited.startsWith(BitVector.fromValidBin("011"))).isTrue()
    assertThat(edited.endsWith(BitVector.fromValidBin("111"))).isTrue()
    assertThat(edited.grouped(4).map(_.toBin).toList.asJava).containsExactly("0111", "1000", "0111")
    assertThat(edited.compare(BitVector.fromValidBin("01111001000"))).isLessThan(0)
    assertThat(edited.acquire(99).isLeft).isTrue()
  }

  @Test
  def bitVectorConvertsNumbersWithSignednessAndByteOrdering(): Unit = {
    val uuid = UUID.fromString("10203040-5060-7080-90a0-b0c0d0e0f001")
    val littleEndianInt = BitVector.fromInt(0x01020304, ordering = ByteOrdering.LittleEndian)
    val thirteenBits = BitVector.fromInt(0x1abc, size = 13)

    assertThat(littleEndianInt.toHex).isEqualTo("04030201")
    assertThat(littleEndianInt.toInt(ordering = ByteOrdering.LittleEndian)).isEqualTo(0x01020304)
    assertThat(BitVector.fromByte(0xff.toByte, size = 4).toByte(signed = true)).isEqualTo(-1.toByte)
    assertThat(BitVector.fromByte(0xff.toByte, size = 4).toByte(signed = false)).isEqualTo(15.toByte)
    assertThat(thirteenBits.toBin).isEqualTo("1101010111100")
    assertThat(thirteenBits.sliceToInt(1, 8, signed = false)).isEqualTo(0xab)
    assertThat(BitVector.fromLong(0x0102030405060708L, size = 40).toLong(signed = false)).isEqualTo(0x0405060708L)
    assertThat(BitVector.fromUUID(uuid).toUUID).isEqualTo(uuid)
    assertThat(BitVector.encodeUtf8("bits").map(_.decodeUtf8)).isEqualTo(Right(Right("bits")))
    assertThat(BitVector.fromValidHex("abc").toBin).isEqualTo("101010111100")
  }

  @Test
  def bitVectorBitwiseReverseShiftRotateAndLazyConstructionWorkTogether(): Unit = {
    val vector = BitVector.unfold(0) { index =>
      if (index < 4) Some((BitVector.fromByte((0xf0 + index).toByte), index + 1))
      else None
    }
    val forced = vector.force

    assertThat(forced.toHex).isEqualTo("f0f1f2f3")
    assertThat(forced.reverse.toBin.take(8)).isEqualTo("11001111")
    assertThat(forced.reverseByteOrder.toHex).isEqualTo("f3f2f1f0")
    assertThat(forced.reverseByteOrder.invertReverseByteOrder).isEqualTo(forced)
    assertThat(forced.reverseBitOrder.toHex).isEqualTo("0f8f4fcf")
    assertThat((forced & BitVector.fromValidHex("0f0f0f0f")).toHex).isEqualTo("00010203")
    assertThat((forced | BitVector.fromValidHex("0f0f0f0f")).toHex).isEqualTo("ffffffff")
    assertThat((forced ^ BitVector.fromValidHex("ffffffff")).toHex).isEqualTo("0f0e0d0c")
    assertThat((~forced).toHex).isEqualTo("0f0e0d0c")
    assertThat(forced.shiftLeft(4).toHex).isEqualTo("0f1f2f30")
    assertThat(forced.shiftRight(4, signExtension = false).toHex).isEqualTo("0f0f1f2f")
    assertThat(forced.rotateLeft(8).toHex).isEqualTo("f1f2f3f0")
    assertThat(forced.rotateRight(8).toHex).isEqualTo("f3f0f1f2")
  }

  @Test
  def crcFunctionsSupportKnownVectorsAndIncrementalBuilders(): Unit = {
    val message = ByteVector.encodeAscii("123456789").getOrElse(ByteVector.empty).bits
    val split = message.splitAt(32)
    val crc32BuilderResult = crc.crc32Builder.updated(split._1).updated(split._2).result
    val customCrc8 = crc(
      poly = BitVector.fromValidHex("07"),
      initial = BitVector.low(8),
      reflectInput = false,
      reflectOutput = false,
      finalXor = BitVector.low(8)
    )

    assertThat(crc.crc32(message).toHex).isEqualTo("cbf43926")
    assertThat(crc32BuilderResult.toHex).isEqualTo("cbf43926")
    assertThat(crc.crc32c(message).toHex).isEqualTo("e3069283")
    assertThat(crc.int32(0x04c11db7, 0xffffffff, reflectInput = true, reflectOutput = true, 0xffffffff)(message))
      .isEqualTo(0xcbf43926)
    assertThat(customCrc8(message).toHex).isEqualTo("f4")
  }

  @Test
  def scalaThreeInterpolatorsProduceValidatedVectors(): Unit = {
    val bytes = hex"de ad be ef"
    val bits = bin"1010_111"

    assertThat(bytes).isEqualTo(ByteVector.fromValidHex("deadbeef"))
    assertThat(bits).isEqualTo(BitVector.fromValidBin("1010111"))
    assertThat((bytes.bits.take(4) ++ bits).toBin).isEqualTo("11011010111")
  }
}

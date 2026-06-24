/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scodec.scodec_core_3

import java.util.UUID

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.Test
import scodec.{Attempt, Codec, DecodeResult, Err, SizeBound}
import scodec.bits.{BitVector, ByteVector}
import scodec.codecs.*

class Scodec_core_3Test {
  private case class Frame(version: Int, messageType: Int, body: String)

  private case class DerivedRecord(id: Int, enabled: Boolean, name: String) derives Codec

  private sealed trait DerivedMessage derives Codec
  private case class DerivedPing(id: Int) extends DerivedMessage
  private case class DerivedData(name: String, payload: ByteVector) extends DerivedMessage

  private sealed trait Command
  private case object Start extends Command
  private case class Write(payload: ByteVector) extends Command
  private case class Numbered(value: Int) extends Command

  private enum Priority {
    case Low, Medium, High
  }

  @Test
  def numericAndPrimitiveCodecsUseDocumentedBinaryLayouts(): Unit = {
    assertRoundTrip(uint8, 255)
    assertRoundTrip(int16, -1234)
    assertRoundTrip(int32L, 0x12345678)
    assertRoundTrip(uint32, 0xffffffffL)
    assertRoundTrip(float, 12.5f)
    assertRoundTrip(doubleL, -0.25d)
    assertRoundTrip(bool, true)
    assertRoundTrip(uuid, UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"))

    assertEquals("1234", uint16.encode(0x1234).require.toHex)
    assertEquals("3412", uint16L.encode(0x1234).require.toHex)
    assertEquals("fffffffe", int32.encode(-2).require.toHex)
    assertEquals("78563412", int32L.encode(0x12345678).require.toHex)
    val exampleUuid: UUID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")
    assertEquals("00112233445566778899aabbccddeeff", uuid.encode(exampleUuid).require.toHex)
  }

  @Test
  def variableSizedCollectionsAndOptionalFieldsRoundTripWithRemainders(): Unit = {
    val payload: ByteVector = ByteVector(0xde, 0xad, 0xbe, 0xef)
    val payloadCodec: Codec[ByteVector] = variableSizeBytes(uint8, bytes)
    val encodedPayload: BitVector = payloadCodec.encode(payload).require

    assertEquals("04deadbeef", encodedPayload.toHex)

    val decodedWithRemainder: DecodeResult[ByteVector] =
      payloadCodec.decode(encodedPayload ++ ByteVector(0xff).bits).require
    assertEquals(payload, decodedWithRemainder.value)
    assertEquals("ff", decodedWithRemainder.remainder.toHex)

    assertRoundTrip(listOfN(uint8, uint4), List(1, 2, 15))
    assertRoundTrip(vectorOfN(uint8, int16), Vector(-3, 0, 4096))
    assertRoundTrip(optional(bool, uint8), Some(42))
    assertRoundTrip(optional(bool, uint8), None)
    assertRoundTrip(conditional(included = true, ascii), Some("included"))
    assertRoundTrip(conditional(included = false, ascii), None)
  }

  @Test
  def tupleCompositionAndCaseClassIsomorphismsBuildStructuredCodecs(): Unit = {
    val frameCodec: Codec[Frame] =
      Codec.fromTuple((uint4, uint4, variableSizeBytes(uint8, utf8))).as[Frame]
    val frame: Frame = Frame(version = 2, messageType = 12, body = "ok")
    val encodedFrame: BitVector = frameCodec.encode(frame).require

    assertEquals("2c026f6b", encodedFrame.toHex)
    assertDecodedCompletely(frameCodec, encodedFrame, frame)

    val consumedCodec: Codec[ByteVector] =
      uint8.consume(length => bytes(length))(body => body.size.toInt)
    val body: ByteVector = ByteVector(1, 2, 3, 4, 5)

    assertEquals("050102030405", consumedCodec.encode(body).require.toHex)
    assertRoundTrip(consumedCodec, body)
  }

  @Test
  def derivedProductAndSumCodecsEncodeAllFields(): Unit = {
    val product: DerivedRecord = DerivedRecord(7, enabled = true, "derived")
    assertRoundTrip(Codec[DerivedRecord], product)

    val ping: DerivedMessage = DerivedPing(99)
    val data: DerivedMessage = DerivedData("blob", ByteVector(1, 3, 3, 7))

    assertRoundTrip(Codec[DerivedMessage], ping)
    assertRoundTrip(Codec[DerivedMessage], data)
    assertEquals("0000000063", Codec[DerivedMessage].encode(ping).require.toHex)
  }

  @Test
  def discriminatorAndMappedEnumCodecsSelectTheExpectedVariant(): Unit = {
    val commandCodec: Codec[Command] = discriminated[Command]
      .by(uint8)
      .singleton(1, Start)
      .typecase(2, variableSizeBytes(uint8, bytes).as[Write])
      .typecase(3, int16.as[Numbered])

    assertEquals("01", commandCodec.encode(Start).require.toHex)
    assertEquals("0203010203", commandCodec.encode(Write(ByteVector(1, 2, 3))).require.toHex)
    assertEquals("03fffb", commandCodec.encode(Numbered(-5)).require.toHex)
    assertRoundTrip(commandCodec, Start)
    assertRoundTrip(commandCodec, Write(ByteVector(9, 8, 7)))
    assertRoundTrip(commandCodec, Numbered(1024))

    val priorityCodec: Codec[Priority] = mappedEnum(
      uint8,
      Priority.Low -> 1,
      Priority.Medium -> 2,
      Priority.High -> 3
    )

    assertEquals("03", priorityCodec.encode(Priority.High).require.toHex)
    assertDecodedCompletely(priorityCodec, BitVector.fromValidHex("02"), Priority.Medium)
    assertTrue(priorityCodec.decode(BitVector.fromValidHex("ff")).isFailure)
  }

  @Test
  def fixedSizeDelimitedAndCompressedCodecsRespectTheirBoundaries(): Unit = {
    val strictBytes: Codec[ByteVector] = bytesStrict(3)
    assertRoundTrip(strictBytes, ByteVector(0xaa, 0xbb, 0xcc))
    assertTrue(strictBytes.encode(ByteVector(0xaa, 0xbb)).isFailure)

    val paddedBytes: Codec[ByteVector] = bytes(4)
    assertEquals("01020000", paddedBytes.encode(ByteVector(1, 2)).require.toHex)
    assertDecodedCompletely(paddedBytes, BitVector.fromValidHex("01020000"), ByteVector(1, 2, 0, 0))

    val delimitedText: Codec[String] =
      variableSizeDelimited(constant(ByteVector(0)), utf8, multipleValueSize = 8)
    assertEquals("68656c6c6f00", delimitedText.encode("hello").require.toHex)
    assertRoundTrip(delimitedText, "hello")

    val compressedText: Codec[String] = zlib(variableSizeBytes(uint8, utf8), chunkSize = 32)
    assertRoundTrip(compressedText, "compressible text compressible text")
  }

  @Test
  def lookaheadCodecsInspectInputWithoutConsumingIt(): Unit = {
    val input: BitVector = BitVector.fromValidHex("2a03616263ff")

    val peekedHeader: DecodeResult[Int] = peek(uint8).decode(input).require
    assertEquals(42, peekedHeader.value)
    assertEquals(input, peekedHeader.remainder)

    val matchedMagic: DecodeResult[Boolean] =
      lookahead(constant(ByteVector(0x2a))).decode(input).require
    assertTrue(matchedMagic.value)
    assertEquals(input, matchedMagic.remainder)

    val missingMagic: DecodeResult[Boolean] =
      lookahead(constant(ByteVector(0x7f))).decode(input).require
    assertFalse(missingMagic.value)
    assertEquals(input, missingMagic.remainder)

    val framedPayloadCodec: Codec[BitVector] = peekVariableSizeBytes(uint8)
    val framedBits: DecodeResult[BitVector] =
      framedPayloadCodec.decode(BitVector.fromValidHex("03616263ff")).require
    assertEquals("03616263", framedBits.value.toHex)
    assertEquals("ff", framedBits.remainder.toHex)
    assertEquals("03616263", framedPayloadCodec.encode(framedBits.value).require.toHex)
  }

  @Test
  def checksummedCodecsValidateFramedPayloadIntegrity(): Unit = {
    val xorChecksum: BitVector => BitVector = bits => {
      val checksumByte: Int = bits.toByteArray.foldLeft(0) { (checksum: Int, byte: Byte) =>
        checksum ^ (byte & 0xff)
      }
      ByteVector(checksumByte).bits
    }
    val frameCodec: Codec[(BitVector, BitVector)] =
      Codec.fromTuple((variableSizeBytes(uint8, bits), bits(8)))
    val textCodec: Codec[String] = checksummed(utf8, xorChecksum, frameCodec)
    val encodedText: BitVector = textCodec.encode("ok").require

    assertEquals("026f6b04", encodedText.toHex)
    assertDecodedCompletely(textCodec, encodedText, "ok")

    val decodedWithRemainder: DecodeResult[String] =
      textCodec.decode(encodedText ++ ByteVector(0xff).bits).require
    assertEquals("ok", decodedWithRemainder.value)
    assertEquals("ff", decodedWithRemainder.remainder.toHex)

    assertTrue(textCodec.decode(BitVector.fromValidHex("026f6b05")).isFailure)
  }

  @Test
  def attemptsErrorsAndSizeBoundsExposeFailureDetails(): Unit = {
    val failedEncode: Attempt[BitVector] = uint8.withContext("header").encode(300)
    assertTrue(failedEncode.isFailure)
    val failureMessage: String = failedEncode.fold(_.messageWithContext, _ => "")
    assertTrue(failureMessage.contains("header"))

    val constantFailure: Attempt[DecodeResult[Unit]] =
      constant(ByteVector(1, 2)).decode(ByteVector(1, 3).bits)
    assertTrue(constantFailure.isFailure)
    val recovered: Attempt[DecodeResult[Unit]] =
      constantFailure.recover { case _: Err => DecodeResult((), BitVector.empty) }
    assertEquals("recovered", recovered.map(_ => "recovered").require)

    assertTrue(uint8.complete.decode(BitVector.fromValidHex("01ff")).isFailure)
    assertEquals(42, Attempt.fromOption(Some(42), Err("missing")).require)
    assertEquals("fallback", Attempt.fromOption[String](None, Err("missing")).getOrElse("fallback"))

    val exactByte: SizeBound = SizeBound.exact(8)
    val variableTail: SizeBound = SizeBound.atLeast(16)
    assertEquals(Some(8L), uint8.sizeBound.exact)
    assertEquals(SizeBound.atLeast(24), exactByte + variableTail)
    assertFalse((exactByte | variableTail).exact.isDefined)
  }

  private def assertRoundTrip[A](codec: Codec[A], value: A): Unit = {
    assertDecodedCompletely(codec, codec.encode(value).require, value)
  }

  private def assertDecodedCompletely[A](codec: Codec[A], bits: BitVector, expected: A): Unit = {
    val decoded: DecodeResult[A] = codec.decode(bits).require
    assertEquals(expected, decoded.value)
    assertTrue(decoded.remainder.isEmpty, s"Unexpected remainder: ${decoded.remainder.toHex}")
  }
}

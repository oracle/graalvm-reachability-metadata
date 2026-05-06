/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scodec.scodec_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.Test
import scodec.{Attempt, Codec, DecodeResult, Decoder, Encoder, Err, SizeBound}
import scodec.Codec.*
import scodec.bits.{BitVector, ByteVector}
import scodec.codecs.*

final case class DerivedFrame(version: Int, urgent: Boolean, label: String) derives Codec

sealed trait DerivedMessage derives Codec
final case class DerivedText(id: Int, body: String) extends DerivedMessage
final case class DerivedFlag(enabled: Boolean) extends DerivedMessage

sealed trait Packet
final case class TextPacket(value: String) extends Packet
final case class CountPacket(value: Int) extends Packet

class Scodec_core_3Test:

  @Test
  def numericCodecsEncodeEndiannessAndPreserveRemainders(): Unit =
    val codec: Codec[(Int, Int, Boolean)] = Codec.fromTuple((uint8, int16L, bool))
    val encoded: BitVector = codec.encode((255, -2, true)).require

    assertEquals("fffeff8", encoded.toHex)
    assertEquals(25L, encoded.size)
    assertEquals(SizeBound.exact(25), codec.sizeBound)

    val decoded: DecodeResult[(Int, Int, Boolean)] = codec.decode(encoded ++ hex("abcd")).require
    assertEquals((255, -2, true), decoded.value)
    assertEquals(hex("abcd"), decoded.remainder)

  @Test
  def fixedSizeConstantsAndDropCombinatorsFramePayloads(): Unit =
    val payloadCodec: Codec[Int] = constant(hex("aa")) ~> uint8 <~ constant(hex("bb"))
    val paddedBytes: Codec[ByteVector] = bytes(4)

    assertEquals(hex("aa07bb"), payloadCodec.encode(7).require)
    assertEquals(7, payloadCodec.decode(hex("aa07bbcc")).require.value)
    assertEquals(hex("cc"), payloadCodec.decode(hex("aa07bbcc")).require.remainder)

    assertEquals(bytesHex("beef0000"), paddedBytes.encode(bytesHex("beef")).require.bytes)
    val decoded: DecodeResult[ByteVector] = paddedBytes.decode(hex("beef0000cafe")).require
    assertEquals(bytesHex("beef0000"), decoded.value)
    assertEquals(hex("cafe"), decoded.remainder)

    val strictFailure: String = failureMessage(bytesStrict(4).encode(bytesHex("beef")))
    assertTrue(strictFailure.contains("exactly 32 bits"))

  @Test
  def variableSizedStringsCollectionsAndOptionalValuesRoundTrip(): Unit =
    val stringCodec: Codec[String] = variableSizeBytes(uint8, utf8)
    val vectorCodec: Codec[Vector[Int]] = vectorOfN(uint8, uint4)
    val optionalCodec: Codec[Option[Int]] = optional(bool, uint8)
    val defaultCodec: Codec[Int] = withDefaultValue(optionalCodec, 99)

    assertEquals(hex("0568656c6c6f"), stringCodec.encode("hello").require)
    val stringResult: DecodeResult[String] = stringCodec.decode(hex("0568656c6c6fcafe")).require
    assertEquals("hello", stringResult.value)
    assertEquals(hex("cafe"), stringResult.remainder)

    assertRoundTrip(vectorCodec, Vector(1, 2, 15, 0))
    assertEquals(Some(42), optionalCodec.decode(optionalCodec.encode(Some(42)).require).require.value)
    assertEquals(None, optionalCodec.decode(optionalCodec.encode(None).require).require.value)
    assertEquals(99, defaultCodec.decode(BitVector.low(1)).require.value)
    assertEquals(12, defaultCodec.decode(defaultCodec.encode(12).require).require.value)

  @Test
  def transformedCodecsReportContextAndCompletionFailures(): Unit =
    val evenByte: Codec[Int] = uint8.exmap(
      value => Attempt.guard(value % 2 == 0, Err(s"not even: $value")).map(_ => value),
      value => Attempt.guard(value % 2 == 0, Err(s"not even: $value")).map(_ => value)
    )
    val contextual: Codec[Int] = evenByte.withContext("payloadLength")

    assertEquals(24, contextual.decode(hex("18ff")).require.value)
    assertTrue(contextual.complete.decode(hex("18ff")).isFailure)

    val encodeFailure: String = failureMessage(contextual.encode(5))
    assertTrue(encodeFailure.contains("payloadLength"))
    assertTrue(encodeFailure.contains("not even: 5"))

    val decodeFailure: String = failureMessage(contextual.decode(hex("05")))
    assertTrue(decodeFailure.contains("payloadLength"))
    assertTrue(decodeFailure.contains("not even: 5"))

  @Test
  def derivedProductAndSumCodecsRoundTripPublicModels(): Unit =
    val frame: DerivedFrame = DerivedFrame(7, urgent = true, "native-image")
    assertRoundTrip(Codec[DerivedFrame], frame)

    val text: DerivedMessage = DerivedText(1001, "status")
    val flag: DerivedMessage = DerivedFlag(enabled = false)
    assertRoundTrip(Codec[DerivedMessage], text)
    assertRoundTrip(Codec[DerivedMessage], flag)

  @Test
  def discriminatedCodecSelectsCasesAndRejectsUnknownTags(): Unit =
    val textCodec: Codec[TextPacket] = variableSizeBytes(uint8, utf8).xmap(TextPacket.apply, _.value)
    val countCodec: Codec[CountPacket] = uint16.xmap(CountPacket.apply, _.value)
    val packetCodec: Codec[Packet] = discriminated[Packet]
      .by(uint8)
      .subcaseP[TextPacket](1) { case text: TextPacket => text }(textCodec)
      .subcaseP[CountPacket](2) { case count: CountPacket => count }(countCodec)

    assertEquals(hex("01026f6b"), packetCodec.encode(TextPacket("ok")).require)
    assertEquals(hex("020201"), packetCodec.encode(CountPacket(513)).require)
    assertEquals(TextPacket("ok"), packetCodec.decode(hex("01026f6bff")).require.value)
    assertEquals(hex("ff"), packetCodec.decode(hex("01026f6bff")).require.remainder)
    assertEquals(CountPacket(513), packetCodec.decode(hex("020201")).require.value)

    val unknownTag: String = failureMessage(packetCodec.decode(hex("09")))
    assertTrue(unknownTag.contains("Unknown discriminator 9"))

  @Test
  def attemptEncoderAndDecoderUtilitiesComposePredictably(): Unit =
    val encoder: Encoder[Int] = uint8.pcontramap[Int](value =>
      Option.when(value >= 0 && value <= 255)(value)
    )
    val decoder: Decoder[Int] = uint8.emap(value =>
      Attempt.guard(value > 0, Err("zero is reserved")).map(_ => value)
    )
    val codec: Codec[Int] = Codec(encoder, decoder)

    assertEquals(hex("2a"), encoder.encode(42).require)
    assertTrue(encoder.encode(-1).isFailure)
    assertEquals(List(1, 2, 3), uint8.collect[List, Int](hex("010203"), None).require.value)
    assertEquals(
      (None, Vector(1, 2, 3)),
      uint8.decodeAll((value: Int) => Vector(value))(Vector.empty[Int], _ ++ _)(hex("010203"))
    )

    assertEquals(5, codec.decode(hex("05")).require.value)
    assertTrue(codec.decode(hex("00")).isFailure)
    assertEquals(123, Attempt.fromOption(Some(123), Err("missing")).require)
    assertFalse(Attempt.guard(false, "guard failed").isSuccessful)

  @Test
  def variableSizeDelimitedCodecTerminatesPayloadsAndPreservesRemainders(): Unit =
    val codec: Codec[String] = variableSizeDelimited(constant(hex("00")), utf8, 8)

    assertEquals(hex("646f6e6500"), codec.encode("done").require)

    val decoded: DecodeResult[String] = codec.decode(hex("646f6e6500cafe")).require
    assertEquals("done", decoded.value)
    assertEquals(hex("cafe"), decoded.remainder)
    assertTrue(codec.decode(hex("646f6e65")).isFailure)

  @Test
  def checksummedCodecAppendsValidatesAndPreservesRemainders(): Unit =
    val checksum: BitVector => BitVector = valueBits => valueBits.take(8).xor(valueBits.drop(8).take(8))
    val framing: Codec[(BitVector, BitVector)] = Codec.fromTuple((bits(16), bits(8)))
    val codec: Codec[Int] = checksummed(uint16, checksum, framing)

    assertEquals(hex("123426"), codec.encode(0x1234).require)

    val decoded: DecodeResult[Int] = codec.decode(hex("123426ff")).require
    assertEquals(0x1234, decoded.value)
    assertEquals(hex("ff"), decoded.remainder)

    val checksumFailure: String = failureMessage(codec.decode(hex("123427")))
    assertTrue(checksumFailure.contains("checksum mismatch"))

  private def assertRoundTrip[A](codec: Codec[A], value: A): BitVector =
    val encoded: BitVector = codec.encode(value).require
    val decoded: DecodeResult[A] = codec.decode(encoded).require
    assertEquals(value, decoded.value)
    assertTrue(decoded.remainder.isEmpty)
    encoded

  private def failureMessage[A](attempt: Attempt[A]): String =
    attempt match
      case Attempt.Failure(cause) => cause.messageWithContext
      case Attempt.Successful(value) => throw new AssertionError(s"expected failure, got $value")

  private def hex(value: String): BitVector = BitVector.fromValidHex(value)

  private def bytesHex(value: String): ByteVector = ByteVector.fromValidHex(value)

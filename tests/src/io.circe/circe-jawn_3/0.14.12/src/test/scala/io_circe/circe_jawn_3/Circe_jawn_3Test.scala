/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_jawn_3

import cats.data.NonEmptyList
import cats.data.Validated
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Error
import io.circe.Json
import io.circe.ParsingFailure
import io.circe.jawn.JawnParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class Circe_jawn_3Test {
  private final case class Message(id: Long, body: String, flags: List[Boolean])

  private given Decoder[Message] = Decoder.instance { cursor =>
    for {
      id <- cursor.get[Long]("id")
      body <- cursor.get[String]("body")
      flags <- cursor.get[List[Boolean]]("flags")
    } yield Message(id, body, flags)
  }

  @Test
  def packageParserParsesNestedDocumentsAndPreservesJsonValues(): Unit = {
    val input: String =
      """
        |{
        |  "id": 9007199254740993,
        |  "name": "circe jawn",
        |  "enabled": true,
        |  "payload": {
        |    "unicode": "snowman \u2603",
        |    "decimal": -12.50,
        |    "items": [null, false, {"k": "v"}]
        |  }
        |}
        |""".stripMargin

    val root: Json = expectRight(io.circe.jawn.parse(input))
    val cursor = root.hcursor

    assertEquals(BigDecimal("9007199254740993"), expectRight(cursor.get[BigDecimal]("id")))
    assertEquals("circe jawn", expectRight(cursor.get[String]("name")))
    assertTrue(expectRight(cursor.get[Boolean]("enabled")))
    assertEquals("snowman ☃", expectRight(cursor.downField("payload").get[String]("unicode")))
    assertEquals(BigDecimal("-12.50"), expectRight(cursor.downField("payload").get[BigDecimal]("decimal")))
    assertTrue(cursor.downField("payload").downField("items").downArray.focus.exists(_.isNull))
  }

  @Test
  def parserParsesTopLevelScalarValuesFromStrings(): Unit = {
    val parser = new JawnParser()

    val nullJson: Json = expectRight(parser.parse(" null "))
    val booleanJson: Json = expectRight(parser.parse("\nfalse\t"))
    val stringJson: Json = expectRight(parser.parse("\"standalone\""))
    val numberJson: Json = expectRight(parser.parse("6.022e23"))

    assertTrue(nullJson.isNull)
    assertEquals(Some(false), booleanJson.asBoolean)
    assertEquals(Some("standalone"), stringJson.asString)
    assertEquals(Some(BigDecimal("6.022E+23")), numberJson.asNumber.flatMap(_.toBigDecimal))
  }

  @Test
  def parsesCharSequencesByteArraysAndByteBuffers(): Unit = {
    val parser = new JawnParser()
    val builder: StringBuilder = new StringBuilder("{\"source\":\"chars\",\"count\":2}")
    val bytes: Array[Byte] = "[1,2,3,4]".getBytes(StandardCharsets.UTF_8)
    val bufferBytes: Array[Byte] = "{\"buffer\":true}".getBytes(StandardCharsets.UTF_8)
    val directBuffer: ByteBuffer = ByteBuffer.allocateDirect(bufferBytes.length)
    directBuffer.put(bufferBytes)
    directBuffer.flip()

    val charJson: Json = expectRight(parser.parseCharSequence(builder))
    val byteArrayJson: Json = expectRight(parser.parseByteArray(bytes))
    val byteBufferJson: Json = expectRight(parser.parseByteBuffer(directBuffer))

    assertEquals("chars", expectRight(charJson.hcursor.get[String]("source")))
    assertEquals(List(1, 2, 3, 4), expectRight(byteArrayJson.as[List[Int]]))
    assertTrue(expectRight(byteBufferJson.hcursor.get[Boolean]("buffer")))
  }

  @Test
  def parsesJsonFromFilesPathsAndReadableChannels(): Unit = {
    val parser = new JawnParser()
    val path: Path = Files.createTempFile("circe-jawn", ".json")
    Files.writeString(path, "{\"file\":\"ok\",\"n\":7}", StandardCharsets.UTF_8)

    val channel: ReadableByteChannel = Channels.newChannel(
      new ByteArrayInputStream("{\"channel\":[\"a\",\"b\"]}".getBytes(StandardCharsets.UTF_8))
    )

    try {
      val fromPath: Json = expectRight(parser.parsePath(path))
      val fromFile: Json = expectRight(parser.parseFile(path.toFile))
      val fromChannel: Json = expectRight(parser.parseChannel(channel))

      assertEquals("ok", expectRight(fromPath.hcursor.get[String]("file")))
      assertEquals(7, expectRight(fromFile.hcursor.get[Int]("n")))
      assertEquals(List("a", "b"), expectRight(fromChannel.hcursor.get[List[String]]("channel")))
    } finally {
      channel.close()
      Files.deleteIfExists(path)
    }
  }

  @Test
  def decodesDomainTypesFromAllInputForms(): Unit = {
    val parser = new JawnParser()
    val json: String = "{\"id\":42,\"body\":\"hello\",\"flags\":[true,false]}"
    val path: Path = Files.createTempFile("circe-jawn-decode", ".json")
    Files.writeString(path, json, StandardCharsets.UTF_8)
    val jsonBytes: Array[Byte] = json.getBytes(StandardCharsets.UTF_8)
    val channel: ReadableByteChannel = Channels.newChannel(new ByteArrayInputStream(jsonBytes))
    val expected: Message = Message(42L, "hello", List(true, false))

    try {
      assertEquals(expected, expectRight(parser.decode[Message](json)))
      assertEquals(expected, expectRight(parser.decodeCharSequence[Message](new StringBuilder(json))))
      assertEquals(expected, expectRight(parser.decodeByteArray[Message](jsonBytes)))
      assertEquals(expected, expectRight(parser.decodeByteBuffer[Message](ByteBuffer.wrap(jsonBytes))))
      assertEquals(expected, expectRight(parser.decodePath[Message](path)))
      assertEquals(expected, expectRight(parser.decodeFile[Message](path.toFile)))
      assertEquals(expected, expectRight(parser.decodeChannel[Message](channel)))
    } finally {
      channel.close()
      Files.deleteIfExists(path)
    }
  }

  @Test
  def accumulatingDecodeReportsMultipleFailuresForArrayElements(): Unit = {
    val parser = new JawnParser()
    val input: String = "[1,\"two\",false,null,5]"

    assertInvalidCountAtLeast(parser.decodeAccumulating[List[Int]](input), 3)
    assertInvalidCountAtLeast(parser.decodeCharSequenceAccumulating[List[Int]](new StringBuilder(input)), 3)
    assertInvalidCountAtLeast(parser.decodeByteArrayAccumulating[List[Int]](input.getBytes(StandardCharsets.UTF_8)), 3)
    assertInvalidCountAtLeast(
      parser.decodeByteBufferAccumulating[List[Int]](ByteBuffer.wrap(input.getBytes(StandardCharsets.UTF_8))),
      3
    )
  }

  @Test
  def accumulatingDecodeWorksForPathFileAndChannelInputs(): Unit = {
    val parser = new JawnParser()
    val path: Path = Files.createTempFile("circe-jawn-accumulating", ".json")
    Files.writeString(path, "[\"bad\",2,true]", StandardCharsets.UTF_8)
    val channel: ReadableByteChannel = Channels.newChannel(
      new ByteArrayInputStream("[\"bad\",2,true]".getBytes(StandardCharsets.UTF_8))
    )

    try {
      assertInvalidCountAtLeast(parser.decodePathAccumulating[List[Int]](path), 2)
      assertInvalidCountAtLeast(parser.decodeFileAccumulating[List[Int]](path.toFile), 2)
      assertInvalidCountAtLeast(parser.decodeChannelAccumulating[List[Int]](channel), 2)
    } finally {
      channel.close()
      Files.deleteIfExists(path)
    }
  }

  @Test
  def distinguishesParsingFailuresFromDecodingFailures(): Unit = {
    val parser = new JawnParser()

    parser.parse("{\"broken\":}") match {
      case Left(failure: ParsingFailure) =>
        assertFalse(failure.message.isBlank)
        assertNotNull(failure.underlying)
      case Right(json) =>
        fail[Unit](s"Expected malformed JSON to fail, but parsed: $json")
    }

    parser.decode[Message]("{\"id\":\"not-a-long\",\"body\":\"hello\",\"flags\":[]}") match {
      case Left(failure: DecodingFailure) =>
        assertFalse(failure.message.isBlank)
        assertTrue(failure.history.nonEmpty)
      case Left(error) =>
        fail[Unit](s"Expected decoding failure, but got: $error")
      case Right(message) =>
        fail[Unit](s"Expected invalid message JSON to fail, but decoded: $message")
    }
  }

  @Test
  def duplicateKeyPolicyDefaultsToAllowingLastValueAndCanRejectDuplicates(): Unit = {
    val duplicateKeys: String = "{\"same\":1,\"same\":2}"

    val defaultJson: Json = expectRight(new JawnParser().parse(duplicateKeys))
    val explicitlyAllowedJson: Json = expectRight(JawnParser(allowDuplicateKeys = true).parse(duplicateKeys))
    val rejected = JawnParser(allowDuplicateKeys = false).parse(duplicateKeys)

    assertEquals(2, expectRight(defaultJson.hcursor.get[Int]("same")))
    assertEquals(2, expectRight(explicitlyAllowedJson.hcursor.get[Int]("same")))
    rejected match {
      case Left(failure) =>
        assertTrue(failure.message.contains("duplicate key"), failure.message)
      case Right(json) =>
        fail[Unit](s"Expected duplicate keys to be rejected, but parsed: $json")
    }
  }

  @Test
  def maxValueSizeLimitsStringsObjectKeysAndNumbers(): Unit = {
    val limitedParser = JawnParser(5)
    val strictLimitedParser = JawnParser(5, allowDuplicateKeys = false)

    assertEquals("short", expectRight(limitedParser.parse("\"short\"")).asString.getOrElse(""))
    assertParsingFailureContains(limitedParser.parse("\"longer\""), "JSON string length")
    assertParsingFailureContains(limitedParser.parse("{\"longer\":1}"), "JSON key length")
    assertParsingFailureContains(limitedParser.parse("123456"), "JSON number length")
    assertParsingFailureContains(strictLimitedParser.parse("{\"a\":1,\"a\":2}"), "duplicate key")
  }

  @Test
  def parserConfiguredWithBothLimitAndDuplicateAllowanceStillDecodesValidDocuments(): Unit = {
    val parser = JawnParser(20, allowDuplicateKeys = true)
    val input: String = "{\"id\":9,\"body\":\"small\",\"flags\":[false,true]}"

    val decoded: Message = expectRight(parser.decode[Message](input))

    assertEquals(Message(9L, "small", List(false, true)), decoded)
    assertEquals("small", expectRight(parser.parse(input)).hcursor.get[String]("body").toOption.getOrElse(""))
  }

  private def assertInvalidCountAtLeast[A](result: Validated[NonEmptyList[Error], A], expectedMinimum: Int): Unit = {
    result match {
      case Validated.Invalid(errors) =>
        val actual: Int = errors.toList.length
        assertTrue(actual >= expectedMinimum, s"Expected at least $expectedMinimum errors, but got $actual: $errors")
      case Validated.Valid(value) =>
        fail[Unit](s"Expected accumulating decode to fail, but decoded: $value")
    }
  }

  private def assertParsingFailureContains(result: Either[ParsingFailure, Json], expectedMessagePart: String): Unit = {
    result match {
      case Left(failure) =>
        assertTrue(failure.message.contains(expectedMessagePart), failure.message)
        assertNotNull(failure.underlying)
      case Right(json) =>
        fail[Unit](s"Expected parsing failure containing '$expectedMessagePart', but parsed: $json")
    }
  }

  private def expectRight[A](result: Either[?, A]): A = {
    result match {
      case Right(value) => value
      case Left(error) => fail[A](s"Expected successful result, but got: $error")
    }
  }
}

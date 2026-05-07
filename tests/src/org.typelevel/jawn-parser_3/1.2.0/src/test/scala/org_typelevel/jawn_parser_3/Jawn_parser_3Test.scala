/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.jawn_parser_3

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.channels.Channels
import java.nio.file.Files

import scala.jdk.CollectionConverters._
import scala.util.Failure
import scala.util.Try

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.typelevel.jawn.AsyncParser
import org.typelevel.jawn.ChannelParser
import org.typelevel.jawn.Facade
import org.typelevel.jawn.IncompleteParseException
import org.typelevel.jawn.ParseException
import org.typelevel.jawn.Parser
import org.typelevel.jawn.Syntax

class Jawn_parser_3Test {
  import Jawn_parser_3Test._

  implicit private val facade: Facade[Json] = JsonFacade

  @Test
  def parsesNestedJsonFromStringWithCustomFacade(): Unit = {
    val json: String = """
      |{
      |  "name": "jawn\nparser",
      |  "unicode": "λ",
      |  "quote": "a \"quoted\" word",
      |  "active": true,
      |  "missing": null,
      |  "numbers": [0, -12.5e+3, 7],
      |  "nested": { "empty": [], "flag": false }
      |}
      |""".stripMargin

    val parsed: Json = Parser.parseUnsafe[Json](json)

    assertThat(parsed).isEqualTo(
      JObject(Map(
        "name" -> JString("jawn\nparser"),
        "unicode" -> JString("λ"),
        "quote" -> JString("a \"quoted\" word"),
        "active" -> JBoolean(true),
        "missing" -> JNull,
        "numbers" -> JArray(List(
          JNumber("0", decIndex = -1, expIndex = -1),
          JNumber("-12.5e+3", decIndex = 3, expIndex = 5),
          JNumber("7", decIndex = -1, expIndex = -1)
        )),
        "nested" -> JObject(Map(
          "empty" -> JArray(Nil),
          "flag" -> JBoolean(false)
        ))
      ))
    )
  }

  @Test
  def parsesEquivalentDocumentsFromAllSynchronousInputTypes(): Unit = {
    val json: String = """{"items":["alpha",2,{"ok":true}],"tail":null}"""
    val expected: Json = JObject(Map(
      "items" -> JArray(List(
        JString("alpha"),
        JNumber("2", decIndex = -1, expIndex = -1),
        JObject(Map("ok" -> JBoolean(true)))
      )),
      "tail" -> JNull
    ))
    val bytes: Array[Byte] = json.getBytes(StandardCharsets.UTF_8)
    val offsetBuffer: ByteBuffer = ByteBuffer.allocate(bytes.length + 4)
    offsetBuffer.put(Array[Byte]('x'.toByte, 'x'.toByte))
    offsetBuffer.put(bytes)
    offsetBuffer.put(Array[Byte]('y'.toByte, 'y'.toByte))
    offsetBuffer.position(2)
    offsetBuffer.limit(2 + bytes.length)

    val channel = Channels.newChannel(new ByteArrayInputStream(bytes))
    val tempFile = Files.createTempFile("jawn-parser", ".json")
    try {
      Files.write(tempFile, bytes)

      val parsedValues: List[Json] = List(
        Parser.parseFromString[Json](json).get,
        Parser.parseFromCharSequence[Json](new StringBuilder(json)).get,
        Parser.parseFromByteArray[Json](bytes).get,
        Parser.parseFromByteBuffer[Json](offsetBuffer).get,
        Parser.parseFromChannel[Json](channel).get,
        Parser.parseFromFile[Json](tempFile.toFile).get,
        Parser.parseFromPath[Json](tempFile.toString).get
      )

      assertThat(parsedValues.asJava).containsOnly(expected)
      assertThat(offsetBuffer.position()).isEqualTo(offsetBuffer.limit())
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  @Test
  def syntaxChecksValidateJsonWithoutMaterializingAnAst(): Unit = {
    val validJson: String = """[{"a":1}, false, "text"]"""
    val invalidJson: String = """[{"a":1},]"""
    val tempFile = Files.createTempFile("jawn-syntax", ".json")
    try {
      Files.writeString(tempFile, validJson, StandardCharsets.UTF_8)

      assertThat(Syntax.checkString(validJson)).isTrue
      assertThat(Syntax.checkString(invalidJson)).isFalse
      assertThat(Syntax.checkByteBuffer(ByteBuffer.wrap(validJson.getBytes(StandardCharsets.UTF_8)))).isTrue
      assertThat(Syntax.checkByteBuffer(ByteBuffer.wrap(invalidJson.getBytes(StandardCharsets.UTF_8)))).isFalse
      assertThat(Syntax.checkChannel(Channels.newChannel(new ByteArrayInputStream(validJson.getBytes(StandardCharsets.UTF_8))))).isTrue
      assertThat(Syntax.checkFile(tempFile.toFile)).isTrue
      assertThat(Syntax.checkPath(tempFile.toString)).isTrue
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  @Test
  def asyncSingleValueParserResumesAcrossChunks(): Unit = {
    val parser = Parser.async[Json](AsyncParser.SingleValue)

    assertThat(rightValues(parser.absorb(" { \"items\" : [" )).asJava).isEmpty
    assertThat(rightValues(parser.absorb("1, \"two\", false" )).asJava).isEmpty
    val values: Vector[Json] = rightValues(parser.finalAbsorb("] }   "))

    assertThat(values.asJava).containsExactly(JObject(Map(
      "items" -> JArray(List(
        JNumber("1", decIndex = -1, expIndex = -1),
        JString("two"),
        JBoolean(false)
      ))
    )))
  }

  @Test
  def asyncValueStreamAndUnwrapArrayModesEmitCompletedValues(): Unit = {
    val streamParser = Parser.async[Json](AsyncParser.ValueStream)

    val firstStreamValues: Vector[Json] = rightValues(streamParser.absorb("true {\"a\":"))
    val remainingStreamValues: Vector[Json] = rightValues(streamParser.finalAbsorb("1} [2,3]"))

    assertThat((firstStreamValues ++ remainingStreamValues).asJava).containsExactly(
      JBoolean(true),
      JObject(Map("a" -> JNumber("1", decIndex = -1, expIndex = -1))),
      JArray(List(
        JNumber("2", decIndex = -1, expIndex = -1),
        JNumber("3", decIndex = -1, expIndex = -1)
      ))
    )

    val arrayParser = Parser.async[Json](AsyncParser.UnwrapArray)
    val unwrappedValues: Vector[Json] =
      rightValues(arrayParser.finalAbsorb("[1, {\"two\": 2}, \"three\"]"))

    assertThat(unwrappedValues.asJava).containsExactly(
      JNumber("1", decIndex = -1, expIndex = -1),
      JObject(Map("two" -> JNumber("2", decIndex = -1, expIndex = -1))),
      JString("three")
    )
  }

  @Test
  def parseFailuresExposeProblemKindAndLocation(): Unit = {
    val invalid: Try[Json] = Parser.parseFromString[Json]("[\n true,\n invalid\n]")
    invalid match {
      case Failure(error: ParseException) =>
        assertThat(error.msg).contains("expected json value")
        assertThat(error.index).isGreaterThan(0)
        assertThat(error.line).isEqualTo(3)
        assertThat(error.col).isEqualTo(2)
      case other =>
        throw new AssertionError(s"Expected ParseException failure, got $other")
    }

    val incomplete: Try[Json] = Parser.parseFromString[Json]("{\"open\": [1, 2")
    incomplete match {
      case Failure(error: IncompleteParseException) =>
        assertThat(error.msg).isEqualTo("exhausted input")
      case other =>
        throw new AssertionError(s"Expected IncompleteParseException failure, got $other")
    }

    val asyncFailure = Parser.async[Json](AsyncParser.SingleValue).finalAbsorb("[1,,2]")
    asyncFailure match {
      case Left(error) =>
        assertThat(error.msg).contains("expected json value")
      case Right(values) =>
        throw new AssertionError(s"Expected async parse failure, got $values")
    }
  }

  @Test
  def channelParserComputesPowerOfTwoBufferSizesAndRejectsInvalidSizes(): Unit = {
    assertThat(ChannelParser.computeBufferSize(0)).isEqualTo(0)
    assertThat(ChannelParser.computeBufferSize(1)).isEqualTo(1)
    assertThat(ChannelParser.computeBufferSize(8)).isEqualTo(8)
    assertThat(ChannelParser.computeBufferSize(9)).isEqualTo(16)
    assertThat(ChannelParser.computeBufferSize(ChannelParser.DefaultBufferSize)).isEqualTo(ChannelParser.DefaultBufferSize)

    assertThat(expectIllegalArgumentException(ChannelParser.computeBufferSize(-1)).getMessage)
      .contains("negative bufferSize")
    assertThat(expectIllegalArgumentException(ChannelParser.computeBufferSize(0x40000001)).getMessage)
      .contains("bufferSize too large")
  }

  private def rightValues(either: Either[ParseException, collection.Seq[Json]]): Vector[Json] =
    either match {
      case Right(values) => values.toVector
      case Left(error) => throw new AssertionError(s"Unexpected parse failure: ${error.msg}")
    }

  private def expectIllegalArgumentException(operation: => Any): IllegalArgumentException =
    assertThrows(
      classOf[IllegalArgumentException],
      new Executable {
        override def execute(): Unit = {
          operation
          ()
        }
      }
    )
}

object Jawn_parser_3Test {
  sealed trait Json
  final case object JNull extends Json
  final case class JBoolean(value: Boolean) extends Json
  final case class JNumber(value: String, decIndex: Int, expIndex: Int) extends Json
  final case class JString(value: String) extends Json
  final case class JArray(values: List[Json]) extends Json
  final case class JObject(values: Map[String, Json]) extends Json

  object JsonFacade extends Facade.SimpleFacade[Json] {
    override def jnull: Json = JNull

    override def jfalse: Json = JBoolean(false)

    override def jtrue: Json = JBoolean(true)

    override def jnum(s: CharSequence, decIndex: Int, expIndex: Int): Json =
      JNumber(s.toString, decIndex, expIndex)

    override def jstring(s: CharSequence): Json = JString(s.toString)

    override def jarray(vs: List[Json]): Json = JArray(vs)

    override def jobject(vs: Map[String, Json]): Json = JObject(vs)
  }
}

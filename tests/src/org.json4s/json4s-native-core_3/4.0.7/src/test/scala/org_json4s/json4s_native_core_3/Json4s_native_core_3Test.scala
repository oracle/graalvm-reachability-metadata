/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_native_core_3

import org.json4s.*
import org.json4s.MonadicJValue.jvalueToMonadic
import org.json4s.ParserUtil.ParseException
import org.json4s.native.Document
import org.json4s.native.JsonMethods
import org.json4s.native.JsonParser
import org.json4s.native.Printer
import org.json4s.prefs.EmptyValueStrategy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.StringReader
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class Json4s_native_core_3Test {
  @Test
  def parsesJsonFromStringsWithEscapesAndNestedStructures(): Unit = {
    val source: String =
      """
        {
          "message": "hello\njson4s",
          "unicode": "\u2603",
          "active": true,
          "missing": null,
          "items": [
            {"id": 1, "name": "first"},
            {"id": 2, "name": "second", "flags": [false, true]}
          ]
        }
      """

    val parsed: JValue = JsonParser.parse(source)

    assertEquals(JString("hello\njson4s"), parsed \ "message")
    assertEquals(JString("☃"), parsed \ "unicode")
    assertEquals(JBool.True, parsed \ "active")
    assertEquals(JNull, parsed \ "missing")
    assertEquals(JInt(1), (parsed \ "items")(0) \ "id")
    assertEquals(JString("second"), (parsed \ "items")(1) \ "name")
    assertEquals(JArray(List(JBool.False, JBool.True)), (parsed \ "items")(1) \ "flags")
  }

  @Test
  def honorsNumericParsingOptions(): Unit = {
    val json: String = """{"small":7,"longValue":9223372036854775807,"decimal":1.25,"scientific":6.02e3}"""

    val defaultParsed: JValue = JsonParser.parse(json)
    assertEquals(JInt(7), defaultParsed \ "small")
    assertEquals(JInt(BigInt("9223372036854775807")), defaultParsed \ "longValue")
    assertEquals(JDouble(1.25d), defaultParsed \ "decimal")
    assertEquals(JDouble(6020.0d), defaultParsed \ "scientific")

    val preciseParsed: JValue = JsonParser.parse(
      json,
      useBigDecimalForDouble = true,
      useBigIntForLong = false
    )
    assertEquals(JLong(7L), preciseParsed \ "small")
    assertEquals(JLong(Long.MaxValue), preciseParsed \ "longValue")
    assertEquals(JDecimal(BigDecimal("1.25")), preciseParsed \ "decimal")
    assertEquals(JDecimal(BigDecimal("6.02e3")), preciseParsed \ "scientific")
  }

  @Test
  def parsesFromReadersAndControlsAutomaticClosing(): Unit = {
    val autoClosedReader: TrackingStringReader = new TrackingStringReader("""{"value":1}""")
    val autoClosed: JValue = JsonParser.parse(autoClosedReader, closeAutomatically = true)
    assertEquals(JInt(1), autoClosed \ "value")
    assertTrue(autoClosedReader.closed)

    val callerManagedReader: TrackingStringReader = new TrackingStringReader("""{"value":2.5}""")
    val callerManaged: JValue = JsonParser.parse(
      callerManagedReader,
      closeAutomatically = false,
      useBigDecimalForDouble = true
    )
    assertEquals(JDecimal(BigDecimal("2.5")), callerManaged \ "value")
    assertFalse(callerManagedReader.closed)
    callerManagedReader.close()
    assertTrue(callerManagedReader.closed)
  }

  @Test
  def returnsOptionalParseResultsInsteadOfThrowing(): Unit = {
    assertEquals(Some(JObject(JField("ok", JBool.True))), JsonParser.parseOpt("""{"ok":true}"""))
    assertEquals(Some(JObject(JField("n", JDecimal(BigDecimal("2.50"))))), JsonParser.parseOpt("""{"n":2.50}""", useBigDecimalForDouble = true))
    assertEquals(None, JsonParser.parseOpt("""{"unterminated": [1, 2}"""))

    val reader: TrackingStringReader = new TrackingStringReader("""{"reader":false}""")
    assertEquals(Some(JObject(JField("reader", JBool.False))), JsonParser.parseOpt(reader, closeAutomatically = true))
    assertTrue(reader.closed)
  }

  @Test
  def reportsMalformedJsonWithParseExceptions(): Unit = {
    val exception: ParseException = assertThrows(classOf[ParseException], () => JsonParser.parse("""{"bad": tru}"""))

    assertTrue(exception.getMessage.contains("expected boolean"))
    assertTrue(exception.getMessage.contains("Near:"))
  }

  @Test
  def exposesLowLevelPullParserTokensFromStringInput(): Unit = {
    val tokens: List[JsonParser.Token] = JsonParser.parse(
      """{"name":"Ada","numbers":[1,9223372036854775807,3.5],"ok":false,"none":null}""",
      parser => collectTokens(parser)
    )

    assertEquals(
      List(
        JsonParser.OpenObj,
        JsonParser.FieldStart("name"),
        JsonParser.StringVal("Ada"),
        JsonParser.FieldStart("numbers"),
        JsonParser.OpenArr,
        JsonParser.IntVal(BigInt(1)),
        JsonParser.IntVal(BigInt("9223372036854775807")),
        JsonParser.DoubleVal(3.5d),
        JsonParser.CloseArr,
        JsonParser.FieldStart("ok"),
        JsonParser.BoolVal(false),
        JsonParser.FieldStart("none"),
        JsonParser.NullVal,
        JsonParser.CloseObj,
        JsonParser.End
      ),
      tokens
    )
  }

  @Test
  def exposesLowLevelPullParserTokensFromReaderWithNumericOptions(): Unit = {
    val reader: StringReader = new StringReader("""[1,2.75,true,null]""")
    val tokens: List[JsonParser.Token] = JsonParser.parse(
      reader,
      parser => collectTokens(parser),
      useBigDecimalForDouble = true,
      useBigIntForLong = false
    )

    assertEquals(
      List(
        JsonParser.OpenArr,
        JsonParser.LongVal(1L),
        JsonParser.BigDecimalVal(BigDecimal("2.75")),
        JsonParser.BoolVal(true),
        JsonParser.NullVal,
        JsonParser.CloseArr,
        JsonParser.End
      ),
      tokens
    )
  }

  @Test
  def rendersCompactsAndPrettyPrintsJsonValues(): Unit = {
    val value: JObject = JObject(
      JField("name", JString("café")),
      JField("quote", JString("he said \"hi\"")),
      JField("numbers", JArray(List(JInt(1), JDecimal(BigDecimal("2.50"))))),
      JField("nested", JObject(JField("enabled", JBool.True))),
      JField("skipMe", JNothing)
    )

    val compact: String = JsonMethods.compact(JsonMethods.render(value, alwaysEscapeUnicode = true))
    assertEquals(
      """{"name":"caf\u00E9","quote":"he said \"hi\"","numbers":[1,2.50],"nested":{"enabled":true}}""",
      compact
    )
    assertEquals(value.removeField(_._1 == "skipMe"), JsonMethods.parse(compact, useBigDecimalForDouble = true))
    assertEquals("1e+500", JsonMethods.compact(JsonMethods.render(JDouble(Double.PositiveInfinity))))

    val pretty: String = JsonMethods.pretty(JsonMethods.render(value))
    assertTrue(pretty.contains("\n"))
    assertTrue(pretty.contains("  \"enabled\":true"))
    assertEquals(value.removeField(_._1 == "skipMe"), JsonMethods.parse(pretty, useBigDecimalForDouble = true))
  }

  @Test
  def appliesEmptyValueStrategiesWhenRendering(): Unit = {
    val source: JObject = JObject(
      JField("present", JString("value")),
      JField("missing", JNothing),
      JField("array", JArray(List(JInt(1), JNothing, JNull)))
    )

    val skipped: String = JsonMethods.compact(JsonMethods.render(source, emptyValueStrategy = EmptyValueStrategy.skip))
    assertEquals("""{"present":"value","array":[1,null]}""", skipped)

    val preserved: String = JsonMethods.compact(JsonMethods.render(source, emptyValueStrategy = EmptyValueStrategy.preserve))
    assertEquals("""{"present":"value","missing":null,"array":[1,null,null]}""", preserved)
  }

  @Test
  def rendersPrimitiveAndSetValues(): Unit = {
    assertEquals("null", JsonMethods.compact(JsonMethods.render(JString(null))))
    assertEquals("null", JsonMethods.compact(JsonMethods.render(JNull)))
    assertEquals("false", JsonMethods.compact(JsonMethods.render(JBool.False)))
    assertEquals("9000000000", JsonMethods.compact(JsonMethods.render(JLong(9000000000L))))

    val renderedSet: String = JsonMethods.compact(JsonMethods.render(JSet(Set(JInt(1), JInt(2)))))
    assertEquals(Set(JInt(1), JInt(2)), JsonParser.parse(renderedSet).children.toSet)
  }

  @Test
  def usesDocumentCombinatorsAndPrinterWriters(): Unit = {
    val document: Document = Document.group(
      Document.nest(
        2,
        Document.text("alpha") :/: Document.text("beta") :/: Document.text("gamma")
      )
    )

    val wideWriter: StringWriter = new StringWriter()
    document.format(80, wideWriter)
    assertEquals("alpha beta gamma", wideWriter.toString)

    val narrowWriter: StringWriter = new StringWriter()
    document.format(5, narrowWriter)
    assertEquals("alpha\n  beta\n  gamma", narrowWriter.toString)

    val compactWriter: StringWriter = new StringWriter()
    val returnedCompactWriter: StringWriter = Printer.compact(document, compactWriter)
    assertSame(compactWriter, returnedCompactWriter)
    assertEquals("alphabetagamma", compactWriter.toString)

    val prettyWriter: StringWriter = new StringWriter()
    val returnedPrettyWriter: StringWriter = Printer.pretty(document, prettyWriter)
    assertSame(prettyWriter, returnedPrettyWriter)
    assertEquals("alpha\n  beta\n  gamma", prettyWriter.toString)
  }

  @Test
  def rendersAndParsesThroughJsonMethodsFacade(): Unit = {
    val source: String = """{"project":"json4s","versions":[3,4],"meta":{"native":true}}"""
    val parsed: JValue = JsonMethods.parse(source)
    val document: Document = JsonMethods.render(parsed)

    assertEquals(source, JsonMethods.compact(document))
    assertEquals(parsed, JsonMethods.parse(JsonMethods.pretty(document)))
    assertEquals(Some(parsed), JsonMethods.parseOpt(source))
    assertEquals(None, JsonMethods.parseOpt("""{"invalid":]"""))
  }

  @Test
  def parsesCustomInputsThroughJsonInputTypeClass(): Unit = {
    given AsJsonInput[JsonEnvelope] = AsJsonInput.stringAsJsonInput.contramap(_.payload)

    val parsed: JValue = JsonMethods.parse(new JsonEnvelope("""{"kind":"custom","tags":["public","api"]}"""))

    assertEquals(JString("custom"), parsed \ "kind")
    assertEquals(JArray(List(JString("public"), JString("api"))), parsed \ "tags")
  }

  @Test
  def acceptsNonStringInputsThroughJsonMethodsFacade(): Unit = {
    val stream: TrackingByteArrayInputStream = new TrackingByteArrayInputStream(
      """{"text":"héllo","source":"stream"}""".getBytes(StandardCharsets.UTF_8)
    )

    val streamParsed: JValue = JsonMethods.parse(stream)

    assertEquals(JString("héllo"), streamParsed \ "text")
    assertEquals(JString("stream"), streamParsed \ "source")
    assertTrue(stream.closed)

    val tempFile: Path = Files.createTempFile("json4s-native-core", ".json")
    try {
      Files.writeString(tempFile, """{"source":"file","numbers":[10,20]}""", StandardCharsets.UTF_8)

      val fileParsed: JValue = JsonMethods.parse(tempFile.toFile)

      assertEquals(JString("file"), fileParsed \ "source")
      assertEquals(JArray(List(JInt(10), JInt(20))), fileParsed \ "numbers")
      assertEquals(Some(fileParsed), JsonMethods.parseOpt(tempFile.toFile))
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  private def collectTokens(parser: JsonParser.Parser): List[JsonParser.Token] = {
    val builder: scala.collection.mutable.ListBuffer[JsonParser.Token] = scala.collection.mutable.ListBuffer.empty[JsonParser.Token]
    var continue: Boolean = true
    while (continue) {
      val token: JsonParser.Token = parser.nextToken
      builder += token
      continue = token != JsonParser.End
    }
    builder.toList
  }

  private final class JsonEnvelope(val payload: String)

  private final class TrackingStringReader(text: String) extends StringReader(text) {
    var closed: Boolean = false

    override def close(): Unit = {
      closed = true
      super.close()
    }
  }

  private final class TrackingByteArrayInputStream(bytes: Array[Byte]) extends ByteArrayInputStream(bytes) {
    var closed: Boolean = false

    override def close(): Unit = {
      closed = true
      super.close()
    }
  }
}

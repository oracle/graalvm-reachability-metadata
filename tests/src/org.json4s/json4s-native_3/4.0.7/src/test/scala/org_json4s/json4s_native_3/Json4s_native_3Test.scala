/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_native_3

import org.json4s.*
import org.json4s.`native`.{Document, Json as NativeJson, JsonParser, Printer, Serialization as NativeSerialization}
import org.json4s.`native`.{compactJson, parseJson, parseJsonOpt, prettyJson, renderJValue}
import org.json4s.`native`.JsonMethods.*
import org.json4s.prefs.EmptyValueStrategy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import java.io.{StringReader, StringWriter}
import scala.collection.mutable.ListBuffer

class Json4s_native_3Test {
  @Test
  def parsesStrictJsonFromStringsAndReaders(): Unit = {
    val jsonText: String =
      """
        |{
        |  "message": "hello\nworld",
        |  "unicode": "caf\u00e9",
        |  "flags": [true, false, null],
        |  "price": 12.50,
        |  "maxLong": 9223372036854775807,
        |  "largeInteger": 922337203685477580812345
        |}
        |""".stripMargin

    val parsedWithBigNumbers: JValue = parse(jsonText, true, true)
    assertEquals(JString("hello\nworld"), parsedWithBigNumbers \ "message")
    assertEquals(JString("café"), parsedWithBigNumbers \ "unicode")
    assertEquals(JArray(List(JBool.True, JBool.False, JNull)), parsedWithBigNumbers \ "flags")
    assertEquals(JDecimal(BigDecimal("12.50")), parsedWithBigNumbers \ "price")
    assertEquals(JInt(BigInt("9223372036854775807")), parsedWithBigNumbers \ "maxLong")
    assertEquals(JInt(BigInt("922337203685477580812345")), parsedWithBigNumbers \ "largeInteger")

    val parsedWithJvmNumbers: JValue = parse(new StringReader("""{"price":12.5,"maxLong":9223372036854775807}"""), false, false)
    assertEquals(JDouble(12.5d), parsedWithJvmNumbers \ "price")
    assertEquals(JLong(9223372036854775807L), parsedWithJvmNumbers \ "maxLong")

    assertEquals(Some(JArray(List(JInt(1), JInt(2), JInt(3)))), parseOpt("[1,2,3]"))
    assertEquals(None, parseOpt("""{"broken": ]}"""))
    assertThrows(classOf[ParserUtil.ParseException], () => parse("""{"broken": ]}"""))
  }

  @Test
  def parsesTokenStreamsWithTheNativeParser(): Unit = {
    val tokens: List[String] = JsonParser.parse(
      new StringReader("""{"name":"Ada","scores":[1,2.5,true,null]}"""),
      parser => {
        val seen: ListBuffer[String] = ListBuffer.empty[String]
        var token = parser.nextToken
        while token != JsonParser.End do
          token.productPrefix match
            case "OpenObj" => seen += "open-object"
            case "CloseObj" => seen += "close-object"
            case "OpenArr" => seen += "open-array"
            case "CloseArr" => seen += "close-array"
            case "FieldStart" => seen += s"field:${token.productElement(0)}"
            case "StringVal" => seen += s"string:${token.productElement(0)}"
            case "IntVal" => seen += s"int:${token.productElement(0)}"
            case "BigDecimalVal" => seen += s"decimal:${token.productElement(0)}"
            case "BoolVal" => seen += s"bool:${token.productElement(0)}"
            case "NullVal" => seen += "null"
            case _ => seen += token.toString
          token = parser.nextToken
        seen.toList
      },
      true,
      true
    )

    assertEquals(
      List(
        "open-object",
        "field:name",
        "string:Ada",
        "field:scores",
        "open-array",
        "int:1",
        "decimal:2.5",
        "bool:true",
        "null",
        "close-array",
        "close-object"
      ),
      tokens
    )
  }

  @Test
  def rendersAstToCompactAndPrettyNativeDocuments(): Unit = {
    val document: JObject = JObject(
      JField("name", JString("café")),
      JField("line", JString("one\ntwo")),
      JField("missing", JNothing),
      JField("items", JArray(List(JInt(1), JNothing, JNull))),
      JField("nested", JObject(JField("enabled", JBool.True)))
    )

    val renderedWithNulls = render(document, true, EmptyValueStrategy.preserve)
    assertEquals(
      """{"name":"caf\u00E9","line":"one\ntwo","missing":null,"items":[1,null,null],"nested":{"enabled":true}}""",
      compact(renderedWithNulls)
    )

    val renderedSkippingEmptyValues = render(
      JObject(
        JField("name", JString("café")),
        JField("line", JString("one\ntwo")),
        JField("missing", JNothing),
        JField("items", JArray(List(JInt(1), JNull))),
        JField("nested", JObject(JField("enabled", JBool.True)))
      ),
      false,
      EmptyValueStrategy.skip
    )
    assertEquals(
      """{"name":"café","line":"one\ntwo","items":[1,null],"nested":{"enabled":true}}""",
      compact(renderedSkippingEmptyValues)
    )

    val prettyText: String = pretty(renderedWithNulls)
    assertTrue(prettyText.contains("\n"))
    assertTrue(prettyText.contains("\\u00E9"))
    assertTrue(prettyText.contains("  \"nested\""))
  }

  @Test
  def formatsCustomDocumentsWithNativePrinterCombinators(): Unit = {
    def append(left: Document, right: Document): Document = right.`::`(left)
    def line(left: Document, right: Document): Document = right.`:/:`(left)

    val fields: Document = line(
      Document.text("name = Ada,"),
      line(Document.text("score = 42"), Document.text(")"))
    )
    val document: Document = Document.group(
      append(
        Document.text("record("),
        Document.nest(2, line(Document.empty, fields))
      )
    )

    val compactWriter: StringWriter = new StringWriter()
    assertSame(compactWriter, Printer.compact(document, compactWriter))
    assertEquals("record(name = Ada,score = 42)", compactWriter.toString)

    val prettyWriter: StringWriter = new StringWriter()
    assertSame(prettyWriter, Printer.pretty(document, prettyWriter))
    assertEquals(
      "record(\n  name = Ada,\n  score = 42\n  )",
      prettyWriter.toString
    )
  }

  @Test
  def buildsJsonIncrementallyWithStreamingWriter(): Unit = {
    val stringWriter: StringWriter = new StringWriter()
    val writer: JsonWriter[StringWriter] = JsonWriter.streamingPretty(stringWriter, true)

    val completedWriter: JsonWriter[StringWriter] = writer
      .startObject()
      .startField("name")
      .string("café")
      .startField("scores")
      .startArray()
      .int(10)
      .bigDecimal(BigDecimal("20.50"))
      .boolean(true)
      .addJValue(JObject(JField("bonus", JInt(3))))
      .endArray()
      .endObject()

    assertSame(stringWriter, completedWriter.result)
    val jsonText: String = stringWriter.toString
    assertTrue(jsonText.contains("\n"))
    assertTrue(jsonText.contains("caf\\u00E9"))

    val parsed: JValue = parse(jsonText, true, true)
    assertEquals(JString("café"), parsed \ "name")
    assertEquals(
      JArray(List(JInt(10), JDecimal(BigDecimal("20.50")), JBool.True, JObject(JField("bonus", JInt(3))))),
      parsed \ "scores"
    )
  }

  @Test
  def exposesPackageLevelParseAndRenderHelpers(): Unit = {
    val parsed: JValue = parseJson("""{"ok":true,"values":[1,2,3]}""")
    assertEquals(JBool.True, parsed \ "ok")
    assertEquals(JArray(List(JInt(1), JInt(2), JInt(3))), parsed \ "values")
    assertEquals(Some(parsed), parseJsonOpt("""{"ok":true,"values":[1,2,3]}"""))
    assertEquals(None, parseJsonOpt("""{"ok":]}"""))

    val rendered = renderJValue(parsed)
    assertEquals("""{"ok":true,"values":[1,2,3]}""", compactJson(rendered))
    assertTrue(prettyJson(rendered).contains("\n"))
  }

  @Test
  def serializesAndReadsCollectionsWithNativeSerialization(): Unit = {
    implicit val formats: Formats = NativeSerialization.formats(NoTypeHints)

    val source: Map[String, Any] = Map(
      "title" -> "Quarterly",
      "active" -> true,
      "ids" -> List(1, 2, 3),
      "amounts" -> List(BigDecimal("12.50"), BigDecimal("19.99")),
      "metadata" -> Map("region" -> "EMEA", "priority" -> 2)
    )

    val jsonText: String = NativeSerialization.write(source)
    val parsed: JValue = parse(jsonText, true, true)
    assertEquals(JString("Quarterly"), parsed \ "title")
    assertEquals(JBool.True, parsed \ "active")
    assertEquals(JArray(List(JInt(1), JInt(2), JInt(3))), parsed \ "ids")
    assertEquals(JArray(List(JDecimal(BigDecimal("12.50")), JDecimal(BigDecimal("19.99")))), parsed \ "amounts")
    assertEquals(JString("EMEA"), parsed \ "metadata" \ "region")
    assertEquals(JInt(2), parsed \ "metadata" \ "priority")

    val readIds: Map[String, List[Int]] = NativeSerialization.read[Map[String, List[Int]]]("""{"ids":[1,2,3]}""")
    assertEquals(Map("ids" -> List(1, 2, 3)), readIds)

    val readFlags: Map[String, Boolean] = NativeSerialization.read[Map[String, Boolean]](new StringReader("""{"enabled":true}"""))
    assertEquals(Map("enabled" -> true), readFlags)

    val prettyWriter: StringWriter = new StringWriter()
    assertSame(prettyWriter, NativeSerialization.writePretty(source, prettyWriter))
    assertTrue(prettyWriter.toString.contains("\n"))
    assertEquals(parsed, parse(prettyWriter.toString, true, true))
  }

  @Test
  def usesNativeJsonFacadeWithExplicitFormats(): Unit = {
    implicit val formats: Formats = DefaultFormats
    val json: NativeJson = NativeJson(formats)

    val writer: StringWriter = new StringWriter()
    val writtenWriter: StringWriter = json.write(List(Map("letter" -> "a"), Map("letter" -> "b")), writer)
    assertSame(writer, writtenWriter)
    assertEquals(
      JArray(List(JObject(JField("letter", JString("a"))), JObject(JField("letter", JString("b"))))),
      json.parse(writer.toString)
    )

    assertEquals(Some(JObject(JField("safe", JInt(1)))), json.parseOpt("""{"safe":1}"""))
    assertEquals(None, json.parseOpt("""{"safe":]}"""))

    val prettyText: String = json.writePretty(Map("message" -> "hello", "count" -> 2))
    val prettyAst: JValue = json.parse(prettyText)
    assertEquals(JString("hello"), prettyAst \ "message")
    assertEquals(JInt(2), prettyAst \ "count")
    assertTrue(prettyText.contains("\n"))
  }
}

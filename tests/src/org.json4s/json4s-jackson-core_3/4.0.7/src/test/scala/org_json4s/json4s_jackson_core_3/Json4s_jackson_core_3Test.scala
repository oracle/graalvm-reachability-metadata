/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_jackson_core_3

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
import org.json4s.*
import org.json4s.MonadicJValue.jvalueToMonadic
import org.json4s.jackson.Json4sScalaModule
import org.json4s.jackson.JsonMethods
import org.json4s.prefs.EmptyValueStrategy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class Json4s_jackson_core_3Test {
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

    val parsed: JValue = JsonMethods.parse(source)

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

    val defaultParsed: JValue = JsonMethods.parse(json)
    assertEquals(JInt(7), defaultParsed \ "small")
    assertEquals(JInt(BigInt("9223372036854775807")), defaultParsed \ "longValue")
    assertEquals(JDouble(1.25d), defaultParsed \ "decimal")
    assertEquals(JDouble(6020.0d), defaultParsed \ "scientific")

    val preciseParsed: JValue = JsonMethods.parse(
      json,
      useBigDecimalForDouble = true,
      useBigIntForLong = false
    )
    assertEquals(JLong(7L), preciseParsed \ "small")
    assertEquals(JLong(Long.MaxValue), preciseParsed \ "longValue")
    assertEquals(JDecimal(BigDecimal("1.25")), preciseParsed \ "decimal")
    assertEquals(JDecimal(BigDecimal("6.02E+3")), preciseParsed \ "scientific")
  }

  @Test
  def parsesFromReadersStreamsAndFiles(): Unit = {
    val reader: StringReader = new StringReader("""{"source":"reader","values":[1,2,3]}""")
    val readerParsed: JValue = JsonMethods.parse(reader)
    assertEquals(JString("reader"), readerParsed \ "source")
    assertEquals(JArray(List(JInt(1), JInt(2), JInt(3))), readerParsed \ "values")

    val stream: ByteArrayInputStream = new ByteArrayInputStream(
      """{"source":"stream","text":"héllo"}""".getBytes(StandardCharsets.UTF_8)
    )
    try {
      val streamParsed: JValue = JsonMethods.parse(stream)
      assertEquals(JString("stream"), streamParsed \ "source")
      assertEquals(JString("héllo"), streamParsed \ "text")
    } finally {
      stream.close()
    }

    val tempFile: Path = Files.createTempFile("json4s-jackson-core", ".json")
    try {
      Files.writeString(tempFile, """{"source":"file","enabled":true}""", StandardCharsets.UTF_8)

      val fileParsed: JValue = JsonMethods.parse(tempFile.toFile)

      assertEquals(JString("file"), fileParsed \ "source")
      assertEquals(JBool.True, fileParsed \ "enabled")
      assertEquals(Some(fileParsed), JsonMethods.parseOpt(tempFile.toFile))
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  @Test
  def returnsOptionalResultsAndReportsMalformedJson(): Unit = {
    assertEquals(Some(JObject(JField("ok", JBool.True))), JsonMethods.parseOpt("""{"ok":true}"""))
    assertEquals(
      Some(JObject(JField("n", JDecimal(BigDecimal("2.50"))))),
      JsonMethods.parseOpt("""{"n":2.50}""", useBigDecimalForDouble = true)
    )
    assertEquals(None, JsonMethods.parseOpt("""{"unterminated":[1,2}"""))

    val exception: JsonParseException = assertThrows(
      classOf[JsonParseException],
      () => JsonMethods.parse("""{"bad": tru}""")
    )
    assertTrue(exception.getMessage.contains("Unrecognized token"))
  }

  @Test
  def rendersCompactsPrettyPrintsAndEscapesJsonValues(): Unit = {
    val value: JObject = JObject(
      JField("name", JString("café")),
      JField("quote", JString("he said \"hi\"")),
      JField("numbers", JArray(List(JInt(1), JDecimal(BigDecimal("2.50"))))),
      JField("nested", JObject(JField("enabled", JBool.True)))
    )

    val escapedCompact: String = JsonMethods.compact(JsonMethods.render(value, alwaysEscapeUnicode = true))
    assertEquals(
      """{"name":"caf\u00E9","quote":"he said \"hi\"","numbers":[1,2.50],"nested":{"enabled":true}}""",
      escapedCompact
    )
    assertEquals(value, JsonMethods.parse(escapedCompact, useBigDecimalForDouble = true))

    val unescapedCompact: String = JsonMethods.compact(JsonMethods.render(value, alwaysEscapeUnicode = false))
    assertTrue(unescapedCompact.contains("café"))

    val pretty: String = JsonMethods.pretty(JsonMethods.render(value))
    assertTrue(pretty.contains(System.lineSeparator()) || pretty.contains("\n"))
    assertTrue(pretty.contains("\"enabled\" : true"))
    assertEquals(value, JsonMethods.parse(pretty, useBigDecimalForDouble = true))
  }

  @Test
  def appliesEmptyValueStrategiesBeforeJacksonSerialization(): Unit = {
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
  def convertsBetweenJson4sAstAndJacksonJsonNodes(): Unit = {
    val ast: JObject = JObject(
      JField("id", JInt(BigInt("12345678901234567890"))),
      JField("name", JString("Ada")),
      JField("active", JBool.True),
      JField("tags", JArray(List(JString("scala"), JNull)))
    )

    val node: JsonNode = JsonMethods.asJsonNode(ast)

    assertTrue(node.isObject)
    assertEquals("Ada", node.get("name").asText())
    assertEquals(BigInt("12345678901234567890").bigInteger, node.get("id").bigIntegerValue())
    assertTrue(node.get("active").asBoolean())
    assertTrue(node.get("tags").isArray)
    assertEquals(ast, JsonMethods.fromJsonNode(node))

    val objectNode = JsonNodeFactory.instance.objectNode()
    objectNode.put("title", "from-node")
    objectNode.put("count", 42L)
    objectNode.set[JsonNode]("nothing", NullNode.instance)
    objectNode.putArray("flags").add(true).add(false)

    val converted: JValue = JsonMethods.fromJsonNode(objectNode)
    assertEquals(JString("from-node"), converted \ "title")
    assertEquals(JInt(42), converted \ "count")
    assertEquals(JNull, converted \ "nothing")
    assertEquals(JArray(List(JBool.True, JBool.False)), converted \ "flags")
  }

  @Test
  def registersJson4sScalaModuleWithJacksonObjectMapper(): Unit = {
    val mapper: ObjectMapper = new ObjectMapper().registerModule(new Json4sScalaModule())
    val value: JObject = JObject(
      JField("longValue", JLong(9000000000L)),
      JField("decimal", JDecimal(BigDecimal("19.95"))),
      JField("items", JArray(List(JString("book"), JBool.False, JNull)))
    )

    val serialized: String = mapper.writeValueAsString(value)
    assertEquals("""{"longValue":9000000000,"decimal":19.95,"items":["book",false,null]}""", serialized)

    val readAsJValue: JValue = mapper.readValue(serialized, classOf[JValue])
    assertEquals(JLong(9000000000L), readAsJValue \ "longValue")
    assertEquals(JDouble(19.95d), readAsJValue \ "decimal")
    assertEquals(JArray(List(JString("book"), JBool.False, JNull)), readAsJValue \ "items")

    val readAsJObject: JObject = mapper.readValue("""{"name":"module"}""", classOf[JObject])
    assertEquals(JObject(JField("name", JString("module"))), readAsJObject)
  }

  @Test
  def usesFacadeTypeClassHelpersAndCustomJsonInputs(): Unit = {
    import org.json4s.DefaultReaders.*
    import org.json4s.DefaultWriters.*

    case class Account(name: String, score: Int)
    implicit val accountWriter: Writer[Account] = Writer.writer2[String, Int, Account](account => (account.name, account.score))("name", "score")
    implicit val accountReader: Reader[Account] = Reader.reader2[String, Int, Account](Account.apply)("name", "score")
    given AsJsonInput[JsonEnvelope] = AsJsonInput.stringAsJsonInput.contramap(_.payload)

    val account: Account = Account("jackson", 100)
    val accountJson: JValue = JsonMethods.asJValue(account)
    assertEquals(JObject(JField("name", JString("jackson")), JField("score", JInt(100))), accountJson)
    assertEquals(account, JsonMethods.fromJValue[Account](accountJson))

    val parsedEnvelope: JValue = JsonMethods.parse(new JsonEnvelope("""{"kind":"custom","tags":["public","api"]}"""))
    assertEquals(JString("custom"), parsedEnvelope \ "kind")
    assertEquals(JArray(List(JString("public"), JString("api"))), parsedEnvelope \ "tags")
  }

  private final class JsonEnvelope(val payload: String)
}

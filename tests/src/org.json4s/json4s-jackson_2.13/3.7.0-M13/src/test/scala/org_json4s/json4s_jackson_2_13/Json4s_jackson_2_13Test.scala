/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_jackson_2_13

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.json4s._
import org.json4s.jackson.Json
import org.json4s.jackson.Json4sScalaModule
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.{Serialization => JacksonSerialization, compactJson, parseJson, parseJsonOpt, prettyJson, renderJValue}
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import scala.jdk.CollectionConverters._

case class JacksonMoney(amount: BigDecimal, currency: String)

case class JacksonAccount(name: String, score: Int)

case class JacksonFieldMappedAccount(givenName: String, accountStatus: String, loginCount: Int)

case class JacksonOptionalProfile(name: String, email: Option[String], scores: Option[List[Int]])

sealed trait JacksonNotification

case class JacksonEmailNotification(subject: String, recipient: String) extends JacksonNotification

case class JacksonSmsNotification(number: String, urgent: Boolean) extends JacksonNotification

case class JacksonNotificationBatch(owner: String, notifications: List[JacksonNotification])

class JacksonMoneySerializer extends CustomSerializer[JacksonMoney](_ => (
  {
    case JObject(fields) =>
      val amount: BigDecimal = fields.collectFirst { case JField("amount", JDecimal(value)) => value }.get
      val currency: String = fields.collectFirst { case JField("currency", JString(value)) => value }.get
      JacksonMoney(amount, currency)
  },
  { case money: JacksonMoney =>
    JObject(
      JField("amount", JDecimal(money.amount)),
      JField("currency", JString(money.currency))
    )
  }
))

class Json4s_jackson_2_13Test {
  @Test
  def parsesJsonFromSupportedInputsAndHonorsNumberModes(): Unit = {
    val jsonText: String =
      """
        |{
        |  "message": "hello\nworld",
        |  "unicode": "café",
        |  "flags": [true, false, null],
        |  "price": 12.50,
        |  "maxLong": 9223372036854775807,
        |  "largeInteger": 922337203685477580812345
        |}
        |""".stripMargin

    val parsedWithBigNumbers: JValue = parse(jsonText, useBigDecimalForDouble = true, useBigIntForLong = true)
    assertEquals(JString("hello\nworld"), parsedWithBigNumbers \ "message")
    assertEquals(JString("café"), parsedWithBigNumbers \ "unicode")
    assertEquals(JArray(List(JBool.True, JBool.False, JNull)), parsedWithBigNumbers \ "flags")
    assertEquals(JDecimal(BigDecimal("12.50")), parsedWithBigNumbers \ "price")
    assertEquals(JInt(BigInt("9223372036854775807")), parsedWithBigNumbers \ "maxLong")
    assertEquals(JInt(BigInt("922337203685477580812345")), parsedWithBigNumbers \ "largeInteger")

    val parsedWithJvmNumbers: JValue = parse(
      new StringReader("""{"price":12.5,"maxLong":9223372036854775807}"""),
      useBigDecimalForDouble = false,
      useBigIntForLong = false
    )
    assertEquals(JDouble(12.5d), parsedWithJvmNumbers \ "price")
    assertEquals(JLong(9223372036854775807L), parsedWithJvmNumbers \ "maxLong")

    val bytes: Array[Byte] = """{"stream":true,"values":[1,2]}""".getBytes(StandardCharsets.UTF_8)
    val inputStream: ByteArrayInputStream = new ByteArrayInputStream(bytes)
    try {
      val parsedFromStream: JValue = parse(inputStream, useBigDecimalForDouble = true)
      assertEquals(JBool.True, parsedFromStream \ "stream")
      assertEquals(JArray(List(JInt(1), JInt(2))), parsedFromStream \ "values")
    } finally {
      inputStream.close()
    }

    val tempFile: Path = Files.createTempFile("json4s-jackson", ".json")
    try {
      Files.write(tempFile, """{"file":"ok","nested":{"n":3}}""".getBytes(StandardCharsets.UTF_8))
      val parsedFromFile: JValue = parse(tempFile.toFile)
      assertEquals(JString("ok"), parsedFromFile \ "file")
      assertEquals(JInt(3), parsedFromFile \ "nested" \ "n")
    } finally {
      Files.deleteIfExists(tempFile)
    }

    assertEquals(Some(JArray(List(JInt(1), JInt(2), JInt(3)))), parseOpt("[1,2,3]"))
    assertEquals(None, parseOpt("""{"broken":]}"""))
  }

  @Test
  def rendersCompactsAndPrettyPrintsJacksonJsonValues(): Unit = {
    val value: JObject = JObject(
      JField("name", JString("café")),
      JField("line", JString("one\ntwo")),
      JField("missing", JNothing),
      JField("items", JArray(List(JInt(1), JNothing, JNull))),
      JField("nested", JObject(JField("enabled", JBool.True)))
    )

    try {
      val preservingFormats: Formats = DefaultFormats.withEscapeUnicode.preservingEmptyValues
      val renderedWithNulls: JValue = render(
        value,
        alwaysEscapeUnicode = preservingFormats.alwaysEscapeUnicode,
        emptyValueStrategy = preservingFormats.emptyValueStrategy
      )
      assertEquals(
        JObject(
          JField("name", JString("café")),
          JField("line", JString("one\ntwo")),
          JField("missing", JNull),
          JField("items", JArray(List(JInt(1), JNull, JNull))),
          JField("nested", JObject(JField("enabled", JBool.True)))
        ),
        renderedWithNulls
      )
      assertEquals(
        "{\"name\":\"caf\\u00E9\",\"line\":\"one\\ntwo\",\"missing\":null,\"items\":[1,null,null],\"nested\":{\"enabled\":true}}",
        compact(renderedWithNulls)
      )

      val prettyText: String = pretty(renderedWithNulls)
      assertTrue(prettyText.contains("\n"))
      assertTrue(prettyText.contains("\"nested\""))
      assertTrue(prettyText.contains("caf\\u00E9"))

      val skippingFormats: Formats = DefaultFormats.skippingEmptyValues
      val renderedSkippingEmptyValues: JValue = render(
        value,
        alwaysEscapeUnicode = skippingFormats.alwaysEscapeUnicode,
        emptyValueStrategy = skippingFormats.emptyValueStrategy
      )
      assertEquals(JNothing, renderedSkippingEmptyValues \ "missing")
      assertEquals(JArray(List(JInt(1), JNothing, JNull)), renderedSkippingEmptyValues \ "items")
    } finally {
      render(
        JNothing,
        alwaysEscapeUnicode = DefaultFormats.alwaysEscapeUnicode,
        emptyValueStrategy = DefaultFormats.emptyValueStrategy
      )
    }
  }

  @Test
  def convertsBetweenJValuesAndJacksonJsonNodes(): Unit = {
    val objectNode: ObjectNode = mapper.createObjectNode()
    objectNode.put("name", "Ada")
    objectNode.put("active", true)
    objectNode.putArray("scores").add(10).add(20)
    objectNode.putObject("address").put("city", "London")

    val value: JValue = fromJsonNode(objectNode)
    assertEquals(JString("Ada"), value \ "name")
    assertEquals(JBool.True, value \ "active")
    assertEquals(JArray(List(JInt(10), JInt(20))), value \ "scores")
    assertEquals(JString("London"), value \ "address" \ "city")

    val jsonNode: JsonNode = asJsonNode(value)
    assertTrue(jsonNode.isObject)
    assertEquals("Ada", jsonNode.get("name").asText())
    assertEquals(20, jsonNode.get("scores").get(1).asInt())
    assertEquals(value, fromJsonNode(jsonNode))
  }

  @Test
  def exposesPackageLevelJacksonHelpers(): Unit = {
    val formats: Formats = DefaultFormats.withEscapeUnicode.preservingEmptyValues
    val value: JObject = JObject(
      JField("name", JString("café")),
      JField("missing", JNothing),
      JField("values", JArray(List(JInt(1), JNothing, JNull)))
    )

    try {
      val rendered: JValue = renderJValue(value)(formats)
      assertEquals(JNull, rendered \ "missing")
      assertEquals(JArray(List(JInt(1), JNull, JNull)), rendered \ "values")
      assertEquals("{\"name\":\"caf\\u00E9\",\"missing\":null,\"values\":[1,null,null]}", compactJson(rendered))
      assertTrue(prettyJson(rendered).contains("\n"))

      val parsed: JValue = parseJson("""{"ok":true,"numbers":[1,2,3]}""")
      assertEquals(JBool.True, parsed \ "ok")
      assertEquals(JArray(List(JInt(1), JInt(2), JInt(3))), parsed \ "numbers")
      assertEquals(Some(parsed), parseJsonOpt("""{"ok":true,"numbers":[1,2,3]}"""))
      assertEquals(None, parseJsonOpt("""{"ok":]}"""))
    } finally {
      render(
        JNothing,
        alwaysEscapeUnicode = DefaultFormats.alwaysEscapeUnicode,
        emptyValueStrategy = DefaultFormats.emptyValueStrategy
      )
    }
  }

  @Test
  def serializesAndReadsCollectionsThroughJacksonSerialization(): Unit = {
    implicit val formats: Formats = JacksonSerialization.formats(NoTypeHints).withBigDecimal

    val source: Map[String, Any] = Map(
      "title" -> "Quarterly",
      "active" -> true,
      "ids" -> List(1, 2, 3),
      "amounts" -> List(BigDecimal("12.50"), BigDecimal("19.99")),
      "metadata" -> Map("region" -> "EMEA", "priority" -> 2)
    )

    val jsonText: String = JacksonSerialization.write(source)
    val parsed: JValue = parse(jsonText, useBigDecimalForDouble = true)
    assertEquals(JString("Quarterly"), parsed \ "title")
    assertEquals(JBool.True, parsed \ "active")
    assertEquals(JArray(List(JInt(1), JInt(2), JInt(3))), parsed \ "ids")
    assertEquals(JArray(List(JDecimal(BigDecimal("12.50")), JDecimal(BigDecimal("19.99")))), parsed \ "amounts")
    assertEquals(JString("EMEA"), parsed \ "metadata" \ "region")
    assertEquals(JInt(2), parsed \ "metadata" \ "priority")

    val readerValue: Map[String, List[Int]] = JacksonSerialization.read[Map[String, List[Int]]](new StringReader("""{"ids":[1,2,3]}"""))
    assertEquals(Map("ids" -> List(1, 2, 3)), readerValue)

    val prettyWriter: StringWriter = new StringWriter()
    assertSame(prettyWriter, JacksonSerialization.writePretty(source, prettyWriter))
    assertTrue(prettyWriter.toString.contains("\n"))
    assertEquals(parsed, parse(prettyWriter.toString, useBigDecimalForDouble = true))

    val outputStream: ByteArrayOutputStream = new ByteArrayOutputStream()
    JacksonSerialization.write(source, outputStream)
    val streamedJson: String = outputStream.toString(StandardCharsets.UTF_8.name())
    assertEquals(parsed, parse(streamedJson, useBigDecimalForDouble = true))
  }

  @Test
  def appliesFieldSerializersWhenWritingAndReadingCaseClasses(): Unit = {
    import org.json4s.FieldSerializer._

    implicit val formats: Formats = JacksonSerialization.formats(NoTypeHints) + FieldSerializer[JacksonFieldMappedAccount](
      renameTo("givenName", "given_name") orElse renameTo("accountStatus", "status"),
      renameFrom("given_name", "givenName") orElse renameFrom("status", "accountStatus")
    )

    val account: JacksonFieldMappedAccount = JacksonFieldMappedAccount("Ada", "active", 7)
    val jsonText: String = JacksonSerialization.write(account)
    val parsed: JValue = parse(jsonText)

    assertEquals(JString("Ada"), parsed \ "given_name")
    assertEquals(JString("active"), parsed \ "status")
    assertEquals(JInt(7), parsed \ "loginCount")
    assertEquals(JNothing, parsed \ "givenName")
    assertEquals(JNothing, parsed \ "accountStatus")

    val restored: JacksonFieldMappedAccount = JacksonSerialization.read[JacksonFieldMappedAccount](
      """{"given_name":"Grace","status":"locked","loginCount":3}"""
    )
    assertEquals(JacksonFieldMappedAccount("Grace", "locked", 3), restored)
  }

  @Test
  def readsAndWritesOptionalCaseClassMembers(): Unit = {
    implicit val formats: Formats = JacksonSerialization.formats(NoTypeHints)

    val profile: JacksonOptionalProfile = JacksonOptionalProfile(
      name = "Ada",
      email = Some("ada@example.com"),
      scores = Some(List(10, 20))
    )
    val jsonText: String = JacksonSerialization.write(profile)
    val parsed: JValue = parse(jsonText)

    assertEquals(JString("Ada"), parsed \ "name")
    assertEquals(JString("ada@example.com"), parsed \ "email")
    assertEquals(JArray(List(JInt(10), JInt(20))), parsed \ "scores")
    assertEquals(profile, JacksonSerialization.read[JacksonOptionalProfile](jsonText))

    val missingOptionalMembers: JacksonOptionalProfile = JacksonSerialization.read[JacksonOptionalProfile](
      """{"name":"Grace"}"""
    )
    assertEquals(JacksonOptionalProfile("Grace", None, None), missingOptionalMembers)

    val nullOptionalMembers: JacksonOptionalProfile = JacksonSerialization.read[JacksonOptionalProfile](
      """{"name":"Linus","email":null,"scores":null}"""
    )
    assertEquals(JacksonOptionalProfile("Linus", None, None), nullOptionalMembers)
  }

  @Test
  def writesAndReadsPolymorphicValuesWithShortTypeHints(): Unit = {
    implicit val formats: Formats = JacksonSerialization.formats(
      ShortTypeHints(
        List(classOf[JacksonEmailNotification], classOf[JacksonSmsNotification]),
        "kind"
      )
    )

    val batch: JacksonNotificationBatch = JacksonNotificationBatch(
      owner = "operations",
      notifications = List(
        JacksonEmailNotification("deployment", "ops@example.com"),
        JacksonSmsNotification("+15550101", urgent = true)
      )
    )

    val jsonText: String = JacksonSerialization.write(batch)
    val parsed: JValue = parse(jsonText)
    assertEquals(JString("operations"), parsed \ "owner")
    assertEquals(JString("JacksonEmailNotification"), (parsed \ "notifications")(0) \ "kind")
    assertEquals(JString("deployment"), (parsed \ "notifications")(0) \ "subject")
    assertEquals(JString("JacksonSmsNotification"), (parsed \ "notifications")(1) \ "kind")
    assertEquals(JBool.True, (parsed \ "notifications")(1) \ "urgent")

    val restored: JacksonNotificationBatch = JacksonSerialization.read[JacksonNotificationBatch](jsonText)
    assertEquals(batch, restored)
  }

  @Test
  def appliesCustomSerializersToCollections(): Unit = {
    implicit val formats: Formats = DefaultFormats.withBigDecimal + new JacksonMoneySerializer

    val prices: List[JacksonMoney] = List(
      JacksonMoney(BigDecimal("19.99"), "EUR"),
      JacksonMoney(BigDecimal("2.50"), "EUR")
    )
    val pricesJson: String = JacksonSerialization.write(prices)
    val pricesValue: JValue = parse(pricesJson, useBigDecimalForDouble = true)

    assertEquals(JDecimal(BigDecimal("19.99")), pricesValue(0) \ "amount")
    assertEquals(JString("EUR"), pricesValue(0) \ "currency")
    assertEquals(JDecimal(BigDecimal("2.50")), pricesValue(1) \ "amount")
    assertEquals(prices, JacksonSerialization.read[List[JacksonMoney]](pricesJson))

    val mappedJson: String = JacksonSerialization.write(Map("total" -> prices.head))
    val mappedValue: JValue = parse(mappedJson, useBigDecimalForDouble = true)
    assertEquals(JDecimal(BigDecimal("19.99")), mappedValue \ "total" \ "amount")
    assertEquals(Map("total" -> prices.head), JacksonSerialization.read[Map[String, JacksonMoney]](mappedJson))
  }

  @Test
  def usesJsonFacadeWithExplicitFormatsAndMapper(): Unit = {
    val customMapper: ObjectMapper = new ObjectMapper()
    customMapper.registerModule(new Json4sScalaModule)
    val json: Json = Json(DefaultFormats.withBigDecimal.withLong, customMapper)

    val parsed: JValue = json.parse("""{"price":12.50,"maxLong":9223372036854775807}""")
    assertEquals(JDecimal(BigDecimal("12.50")), parsed \ "price")
    assertEquals(JLong(9223372036854775807L), parsed \ "maxLong")
    assertEquals(None, json.parseOpt("""{"broken":]}"""))

    val writer: StringWriter = new StringWriter()
    val returnedWriter: StringWriter = json.write(List(Map("letter" -> "a"), Map("letter" -> "b")), writer)
    assertSame(writer, returnedWriter)
    assertEquals(
      JArray(List(JObject(JField("letter", JString("a"))), JObject(JField("letter", JString("b"))))),
      json.parse(writer.toString)
    )

    val prettyText: String = json.writePretty(Map("message" -> "hello", "count" -> 2))
    val prettyAst: JValue = json.parse(prettyText)
    assertEquals(JString("hello"), prettyAst \ "message")
    assertEquals(JLong(2L), prettyAst \ "count")
    assertTrue(prettyText.contains("\n"))
  }

  @Test
  def integratesJson4sAstWithJacksonObjectMapperModule(): Unit = {
    val jacksonMapper: ObjectMapper = new ObjectMapper()
    jacksonMapper.registerModule(new Json4sScalaModule)
    jacksonMapper.configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true)
    jacksonMapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)

    val value: JValue = JObject(
      JField("title", JString("module")),
      JField("omitted", JNothing),
      JField("numbers", JArray(List(JInt(BigInt("1234567890123456789012345")), JDecimal(BigDecimal("10.25"))))),
      JField("labels", JSet(Set(JString("json4s"), JString("jackson")))),
      JField("presentNull", JNull)
    )

    val jsonText: String = jacksonMapper.writeValueAsString(value)
    val jsonNode: JsonNode = jacksonMapper.readTree(jsonText)
    assertEquals("module", jsonNode.get("title").asText())
    assertFalse(jsonNode.has("omitted"))
    assertTrue(jsonNode.get("labels").isArray)
    assertEquals(Set("json4s", "jackson"), jsonNode.get("labels").elements().asScala.map(_.asText()).toSet)
    assertTrue(jsonNode.get("presentNull").isNull)

    val restored: JValue = jacksonMapper.readValue(jsonText, classOf[JValue])
    assertEquals(JString("module"), restored \ "title")
    assertEquals(JNothing, restored \ "omitted")
    assertEquals(JInt(BigInt("1234567890123456789012345")), (restored \ "numbers")(0))
    assertEquals(JDecimal(BigDecimal("10.25")), (restored \ "numbers")(1))
    val restoredLabels: Set[String] = (restored \ "labels") match {
      case JArray(labels) => labels.collect { case JString(label) => label }.toSet
      case other => fail(s"Expected labels array but got $other")
    }
    assertEquals(Set("json4s", "jackson"), restoredLabels)
    assertEquals(JNull, restored \ "presentNull")

    val objectValue: JObject = jacksonMapper.readValue("""{"active":true,"count":3}""", classOf[JObject])
    assertEquals(JBool.True, objectValue \ "active")
    assertEquals(JInt(3), objectValue \ "count")
  }

  @Test
  def usesJacksonJsonMethodsWithPublicReadersAndWriters(): Unit = {
    import org.json4s.DefaultReaders._
    import org.json4s.DefaultWriters._

    implicit val accountWriter: Writer[JacksonAccount] = new Writer[JacksonAccount] {
      override def write(account: JacksonAccount): JValue = JObject(
        JField("name", JString(account.name)),
        JField("score", JInt(account.score))
      )
    }
    implicit def listWriter[T](implicit valueWriter: Writer[T]): Writer[List[T]] = new Writer[List[T]] {
      override def write(values: List[T]): JValue = JArray(values.map(valueWriter.write))
    }
    implicit val accountReader: Reader[JacksonAccount] = new Reader[JacksonAccount] {
      override def read(value: JValue): JacksonAccount = {
        val name: String = (value \ "name") match {
          case JString(text) => text
          case other => fail(s"Expected account name string but got $other")
        }
        val score: Int = (value \ "score") match {
          case JInt(number) => number.intValue
          case JLong(number) => number.toInt
          case other => fail(s"Expected account score integer but got $other")
        }
        JacksonAccount(name, score)
      }
    }

    val account: JacksonAccount = JacksonAccount("jackson", 42)
    val accountJson: JValue = asJValue(account)
    assertEquals(JObject(JField("name", JString("jackson")), JField("score", JInt(42))), accountJson)
    assertEquals(account, fromJValue[JacksonAccount](accountJson))

    val collectionJson: JValue = asJValue(Map("ids" -> List(1, 2, 3), "scores" -> List(10, 20)))
    assertEquals(JArray(List(JInt(1), JInt(2), JInt(3))), collectionJson \ "ids")
    assertEquals(Map("ids" -> List(1, 2, 3)), fromJValue[Map[String, List[Int]]](JObject(JField("ids", JArray(List(JInt(1), JInt(2), JInt(3)))))))
  }
}

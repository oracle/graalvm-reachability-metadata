/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_ws_standalone_json_2_13

import java.net.URI
import java.time.LocalDate
import java.util.Collections
import java.util.Optional

import scala.jdk.CollectionConverters._

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.ws.{BodyWritable => ScalaBodyWritable}
import play.api.libs.ws.InMemoryBody
import play.api.libs.ws.{JsonBodyReadables => ScalaJsonBodyReadables}
import play.api.libs.ws.{JsonBodyWritables => ScalaJsonBodyWritables}
import play.api.libs.ws.{StandaloneWSResponse => ScalaStandaloneWSResponse}
import play.api.libs.ws.{WSCookie => ScalaWSCookie}
import play.libs.ws.{BodyReadable => JavaBodyReadable}
import play.libs.ws.{BodyWritable => JavaBodyWritable}
import play.libs.ws.DefaultObjectMapper
import play.libs.ws.{JsonBodyReadables => JavaJsonBodyReadables}
import play.libs.ws.{JsonBodyWritables => JavaJsonBodyWritables}
import play.libs.ws.{StandaloneWSResponse => JavaStandaloneWSResponse}
import play.libs.ws.{WSCookie => JavaWSCookie}

class Play_ws_standalone_json_2_13Test {
  @Test
  def scalaReadableParsesResponseBodyAsPlayJson(): Unit = {
    val response: ScalaStandaloneWSResponse = ScalaTestResponse(
      """{"message":"hello","count":2,"nested":{"enabled":true},"items":[1,2,3]}"""
    )

    val parsed: JsValue = response.body[JsValue](ScalaJsonBodyReadables.readableAsJson)

    assertEquals("hello", (parsed \ "message").as[String])
    assertEquals(2, (parsed \ "count").as[Int])
    assertTrue((parsed \ "nested" \ "enabled").as[Boolean])
    assertEquals(Seq(1, 2, 3), (parsed \ "items").as[Seq[Int]])
  }

  @Test
  def scalaWritableSerializesPlayJsonAsInMemoryJsonBody(): Unit = {
    val json: JsValue = Json.obj(
      "library" -> "play-ws-standalone-json",
      "active" -> true,
      "numbers" -> Json.arr(1, 2, 3)
    )

    val writable: ScalaBodyWritable[JsValue] = ScalaJsonBodyWritables.writeableOf_JsValue
    val body: InMemoryBody = writable.transform(json).asInstanceOf[InMemoryBody]

    assertEquals("application/json", writable.contentType)
    assertEquals(json, Json.parse(body.bytes.toArray))
  }

  @Test
  def scalaWritableSerializesJacksonJsonNodeWithProvidedMapper(): Unit = {
    val mapper: ObjectMapper = new ObjectMapper()
    val root = mapper.createObjectNode()
    root.put("title", "Jackson node")
    root.putObject("metadata").put("nativeImage", true)
    root.putArray("tags").add("json").add("ws")

    val writable: ScalaBodyWritable[JsonNode] = ScalaJsonBodyWritables.body(mapper)
    val body: InMemoryBody = writable.transform(root).asInstanceOf[InMemoryBody]
    val parsed: JsonNode = mapper.readTree(body.bytes.toArray)

    assertEquals("application/json", writable.contentType)
    assertEquals("Jackson node", parsed.get("title").asText())
    assertTrue(parsed.get("metadata").get("nativeImage").asBoolean())
    assertEquals(Seq("json", "ws"), parsed.get("tags").elements().asScala.map(_.asText()).toSeq)
  }

  @Test
  def javaReadableParsesJsonWithDefaultAndCustomObjectMappers(): Unit = {
    val response: JavaStandaloneWSResponse = JavaTestResponse(
      """{"name":"standalone","features":["read","write"],"ok":true}"""
    )
    val customMapper: ObjectMapper = new ObjectMapper()

    val defaultNode: JsonNode = JavaJsonBodyReadables.instance.json().apply(response)
    val customNode: JsonNode = JavaJsonBodyReadables.instance.json(customMapper).apply(response)

    assertEquals("standalone", defaultNode.get("name").asText())
    assertTrue(defaultNode.get("ok").asBoolean())
    assertEquals(defaultNode, customNode)
    assertEquals("write", customNode.get("features").get(1).asText())
    assertEquals(defaultNode, response.getBody(JavaJsonBodyReadables.instance.json()))
  }

  @Test
  def javaReadableWrapsInvalidJsonParsingFailures(): Unit = {
    val readable: JavaBodyReadable[JsonNode] = JavaJsonBodyReadables.instance.json(new ObjectMapper())

    val thrown: RuntimeException = assertThrows(
      classOf[RuntimeException],
      () => readable.apply(JavaTestResponse("{not-json}"))
    )

    assertEquals("Error parsing JSON from WS response wsBody", thrown.getMessage)
    assertNotNull(thrown.getCause)
  }

  @Test
  def javaWritableCreatesPrettyPrintedInMemoryJsonBody(): Unit = {
    val mapper: ObjectMapper = new ObjectMapper()
    val node = mapper.createObjectNode()
    node.put("alpha", 1)
    node.putObject("nested").put("beta", "two")

    val writable: JavaBodyWritable[ByteString] = JavaJsonBodyWritables.instance.body(node, mapper)
    val byteString: ByteString = writable.body().get()
    val serialized: String = byteString.utf8String
    val parsed: JsonNode = mapper.readTree(byteString.toArray)

    assertEquals("application/json", writable.contentType())
    assertTrue(serialized.contains(System.lineSeparator()) || serialized.contains("\n"))
    assertEquals(1, parsed.get("alpha").asInt())
    assertEquals("two", parsed.get("nested").get("beta").asText())
  }

  @Test
  def javaWritableUsesDefaultObjectMapperOverload(): Unit = {
    val node = DefaultObjectMapper.instance.createObjectNode()
    node.put("name", "default mapper")
    node.putArray("values").add(4).add(5).add(6)

    val writable: JavaBodyWritable[ByteString] = JavaJsonBodyWritables.instance.body(node)
    val byteString: ByteString = writable.body().get()
    val parsed: JsonNode = DefaultObjectMapper.instance.readTree(byteString.toArray)

    assertEquals("application/json", writable.contentType())
    assertEquals("default mapper", parsed.get("name").asText())
    assertEquals(Seq(4, 5, 6), parsed.get("values").elements().asScala.map(_.asInt()).toSeq)
  }

  @Test
  def defaultObjectMapperSerializesPlayJsonValues(): Unit = {
    val mapper: ObjectMapper = DefaultObjectMapper.instance
    val json: JsValue = Json.obj(
      "name" -> "play",
      "enabled" -> true,
      "payload" -> Json.obj("count" -> 3),
      "items" -> Json.arr("alpha", 7, Json.obj("nested" -> true))
    )

    val serialized: String = mapper.writeValueAsString(json)
    val parsed: JsValue = Json.parse(serialized)

    assertFalse(serialized.isEmpty)
    assertEquals(json, parsed)
  }

  @Test
  def defaultObjectMapperSupportsPlayJsonJdk8AndJavaTimeTypes(): Unit = {
    val mapper: ObjectMapper = DefaultObjectMapper.instance

    val playJson: JsValue = mapper.readValue("""{"framework":"play","json":true}""", classOf[JsValue])
    val optionalJson: String = mapper.writeValueAsString(Optional.of("configured"))
    val localDateJson: String = mapper.writeValueAsString(LocalDate.of(2024, 1, 2))

    assertSame(mapper, DefaultObjectMapper.instance)
    assertEquals(Json.obj("framework" -> "play", "json" -> true), playJson)
    assertEquals("\"configured\"", optionalJson)
    assertFalse(localDateJson.isEmpty)
    assertTrue(localDateJson.contains("2024"))
  }

  private final case class ScalaTestResponse(
      bodyText: String,
      bodyBytes: ByteString = ByteString.empty,
      responseHeaders: Map[String, Seq[String]] = Map("Content-Type" -> Seq("application/json"))
  ) extends ScalaStandaloneWSResponse {
    private val bytes: ByteString = if (bodyBytes.isEmpty) ByteString.fromString(bodyText) else bodyBytes

    override def uri: URI = URI.create("https://example.test/scala-json")

    override def headers: Map[String, Seq[String]] = responseHeaders

    override def underlying[T]: T = bodyText.asInstanceOf[T]

    override def status: Int = 200

    override def statusText: String = "OK"

    override def cookies: Seq[ScalaWSCookie] = Seq.empty

    override def cookie(name: String): Option[ScalaWSCookie] = None

    override def body: String = bodyText

    override def bodyAsBytes: ByteString = bytes

    override def bodyAsSource: Source[ByteString, _] = Source.single(bytes)
  }

  private final case class JavaTestResponse(bodyText: String) extends JavaStandaloneWSResponse {
    private val bytes: ByteString = ByteString.fromString(bodyText)

    override def getUri: URI = URI.create("https://example.test/java-json")

    override def getHeaders: java.util.Map[String, java.util.List[String]] =
      Collections.singletonMap("Content-Type", Collections.singletonList("application/json"))

    override def getUnderlying: Object = bodyText

    override def getStatus: Int = 200

    override def getStatusText: String = "OK"

    override def getCookies: java.util.List[JavaWSCookie] = Collections.emptyList[JavaWSCookie]()

    override def getCookie(name: String): Optional[JavaWSCookie] = Optional.empty[JavaWSCookie]()

    override def getContentType: String = "application/json"

    override def getBody[T](readable: JavaBodyReadable[T]): T = readable.apply(this)

    override def getBody: String = bodyText

    override def getBodyAsBytes: ByteString = bytes
  }
}

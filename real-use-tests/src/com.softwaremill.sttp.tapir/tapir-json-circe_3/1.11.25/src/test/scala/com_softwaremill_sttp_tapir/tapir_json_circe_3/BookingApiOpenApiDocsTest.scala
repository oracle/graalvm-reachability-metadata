/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import io.circe.ACursor
import io.circe.Codec as CirceCodec
import io.circe.HCursor
import io.circe.Json
import io.circe.syntax.*
import io.circe.yaml.parser as yamlParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sttp.apispec.openapi.OpenAPI
import sttp.apispec.openapi.circe.*
import sttp.apispec.openapi.circe.yaml.*
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.json.circe.*

/**
 * OpenAPI documentation generated from endpoints whose JSON bodies are declared with `jsonBody`
 * and circe codecs, rendered to JSON and YAML.
 *
 * A room booking service publishes its API description for client teams: request and response
 * models become schema components, error bodies are documented per status code, and the body
 * examples are encoded with the same circe codecs that serve real requests.
 */
class BookingApiOpenApiDocsTest {
  import BookingApiOpenApiDocsTest.*

  private val docs: OpenAPI = OpenAPIDocsInterpreter().toOpenAPI(List(createBooking, listBookings), "Booking API", "2.1.0")
  private val json: Json = docs.asJson
  private val root: HCursor = json.hcursor

  @Test
  def describesTheServiceAndItsJsonOperations(): Unit = {
    assertEquals(Right("Booking API"), root.downField("info").get[String]("title"))
    assertEquals(Right("2.1.0"), root.downField("info").get[String]("version"))

    val post: ACursor = root.downField("paths").downField("/bookings").downField("post")
    assertEquals(Right("createBooking"), post.get[String]("operationId"))
    assertEquals(
      Right("#/components/schemas/NewBooking"),
      post.downField("requestBody").downField("content").downField("application/json").downField("schema").get[String]("$ref")
    )
    assertEquals(Right(true), post.downField("requestBody").get[Boolean]("required"))
    assertEquals(
      Right("#/components/schemas/Booking"),
      jsonContent(post.downField("responses").downField("201")).downField("schema").get[String]("$ref")
    )
    assertEquals(
      Right("#/components/schemas/BookingError"),
      jsonContent(post.downField("responses").downField("409")).downField("schema").get[String]("$ref")
    )

    val get: ACursor = root.downField("paths").downField("/bookings").downField("get")
    val listSchema: ACursor = jsonContent(get.downField("responses").downField("200")).downField("schema")
    assertEquals(Right("array"), listSchema.get[String]("type"))
    assertEquals(Right("#/components/schemas/Booking"), listSchema.downField("items").get[String]("$ref"))
    assertEquals(Right("room"), get.downField("parameters").downN(0).get[String]("name"))
    assertEquals(Right("query"), get.downField("parameters").downN(0).get[String]("in"))
  }

  @Test
  def turnsCirceModelsIntoSchemaComponents(): Unit = {
    val schemas: ACursor = root.downField("components").downField("schemas")

    val newBooking: ACursor = schemas.downField("NewBooking")
    assertEquals(Right("object"), newBooking.get[String]("type"))
    assertEquals(Right(List("room", "guest", "nights")), newBooking.get[List[String]]("required"))
    assertEquals(Right("integer"), newBooking.downField("properties").downField("nights").get[String]("type"))
    assertEquals(Right("int32"), newBooking.downField("properties").downField("nights").get[String]("format"))
    assertEquals(Right("string"), newBooking.downField("properties").downField("remarks").get[String]("type"))

    val booking: ACursor = schemas.downField("Booking")
    assertEquals(Right("int64"), booking.downField("properties").downField("id").get[String]("format"))
    assertEquals(
      Right("#/components/schemas/NewBooking"),
      booking.downField("properties").downField("request").get[String]("$ref")
    )
    assertEquals(
      Right("Booking confirmation code shown to the guest"),
      booking.downField("properties").downField("confirmation").get[String]("description")
    )

    val error: ACursor = schemas.downField("BookingError")
    // Collection fields may be empty, so tapir documents them as optional.
    assertEquals(Right(List("reason")), error.get[List[String]]("required"))
    assertEquals(Right("array"), error.downField("properties").downField("conflictingIds").get[String]("type"))
  }

  @Test
  def encodesBodyExamplesWithTheCirceCodec(): Unit = {
    val post: ACursor = root.downField("paths").downField("/bookings").downField("post")
    val requestExample: ACursor =
      post.downField("requestBody").downField("content").downField("application/json").downField("example")
    assertEquals(Right(sampleRequest), requestExample.as[NewBooking])

    val responseExample: ACursor = jsonContent(post.downField("responses").downField("201")).downField("example")
    assertEquals(Right(sampleBooking), responseExample.as[Booking])
    assertEquals(Right("BK-0007"), responseExample.get[String]("confirmation"))
  }

  @Test
  def rendersTheSameDocumentAsYaml(): Unit = {
    val yaml: String = docs.toYaml

    assertTrue(yaml.startsWith("openapi: 3.1.0"), yaml)
    assertTrue(yaml.contains("title: Booking API"), yaml)
    assertTrue(yaml.contains("  /bookings:"), yaml)
    assertTrue(yaml.contains("$ref: '#/components/schemas/BookingError'"), yaml)

    val reparsed: Json = yamlParser.parse(yaml).fold(throw _, identity)
    assertEquals(json.deepDropNullValues, reparsed.deepDropNullValues)
  }

  private def jsonContent(response: ACursor): ACursor =
    response.downField("content").downField("application/json")
}

object BookingApiOpenApiDocsTest {
  final case class NewBooking(room: String, guest: String, nights: Int, remarks: Option[String] = None)
      derives CirceCodec.AsObject, Schema

  final case class Booking(
      id: Long,
      request: NewBooking,
      @Schema.annotations.description("Booking confirmation code shown to the guest")
      confirmation: String
  ) derives CirceCodec.AsObject, Schema

  final case class BookingError(reason: String, conflictingIds: List[Long]) derives CirceCodec.AsObject, Schema

  val sampleRequest: NewBooking = NewBooking("Orion", "grace@example.com", 2, Some("late arrival"))
  val sampleBooking: Booking = Booking(7L, sampleRequest, "BK-0007")

  val createBooking: PublicEndpoint[NewBooking, BookingError, Booking, Any] = endpoint.post
    .name("createBooking")
    .in("bookings")
    .in(jsonBody[NewBooking].example(sampleRequest))
    .out(statusCode(StatusCode.Created))
    .out(jsonBody[Booking].example(sampleBooking))
    .errorOut(statusCode(StatusCode.Conflict))
    .errorOut(jsonBody[BookingError])

  val listBookings: PublicEndpoint[Option[String], Unit, List[Booking], Any] = endpoint.get
    .in("bookings")
    .in(query[Option[String]]("room"))
    .out(jsonBody[List[Booking]])
}

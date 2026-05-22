/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.JsonObject
import io.circe.Printer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import sttp.tapir.DecodeResult
import sttp.tapir.EndpointIO
import sttp.tapir.EndpointInput
import sttp.tapir.PublicEndpoint
import sttp.tapir.RawBodyType
import sttp.tapir.Schema
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType
import sttp.tapir.endpoint
import sttp.tapir.json.circe.*
import sttp.tapir.stringBody
import sttp.tapir.stringToPath

import java.nio.charset.StandardCharsets

final case class Book(title: String, pages: Int, tags: List[String], available: Boolean)

object Book {
  given Encoder[Book] = Encoder.forProduct4("title", "pages", "tags", "available") { book =>
    (book.title, book.pages, book.tags, book.available)
  }

  given Decoder[Book] = Decoder.forProduct4("title", "pages", "tags", "available")(Book.apply)

  given Schema[Book] = Schema.anyObject[Book].name(SName("Book"))
}

final case class Search(term: String, limit: Int)

object Search {
  given Encoder[Search] = Encoder.forProduct2("term", "limit") { search =>
    (search.term, search.limit)
  }

  given Decoder[Search] = Decoder.forProduct2("term", "limit")(Search.apply)

  given Schema[Search] = Schema.anyObject[Search].name(SName("Search"))
}

class Tapir_json_circe_3Test {
  @Test
  def circeCodecEncodesAndDecodesJsonCaseClasses(): Unit = {
    val book: Book = Book("Native Tapir", 320, List("scala", "json"), available = true)
    val codec: Codec.JsonCodec[Book] = circeCodec[Book]

    val encoded: String = codec.encode(book)
    val decoded: DecodeResult[Book] = codec.decode(encoded)

    assertEquals(CodecFormat.Json(), codec.format)
    assertEquals(DecodeResult.Value(book), decoded)
    assertTrue(encoded.contains("\"title\":\"Native Tapir\""), encoded)
    assertTrue(encoded.contains("\"pages\":320"), encoded)
  }

  @Test
  def circeCodecReportsParsingAndFieldDecodingFailures(): Unit = {
    val codec: Codec.JsonCodec[Book] = circeCodec[Book]

    codec.decode("not-json") match {
      case DecodeResult.Error(original, error: DecodeResult.Error.JsonDecodeException) =>
        assertEquals("not-json", original)
        assertTrue(error.errors.exists(_.path.isEmpty), error.toString)
        assertFalse(error.errors.exists(_.msg.isBlank), error.toString)
      case other => fail(s"Expected a JSON parsing error, got: $other")
    }

    val invalidPages: String = """{"title":"Native Tapir","pages":"many","tags":[],"available":true}"""
    codec.decode(invalidPages) match {
      case DecodeResult.Error(original, error: DecodeResult.Error.JsonDecodeException) =>
        assertEquals(invalidPages, original)
        assertTrue(error.errors.exists(_.path.map(_.name).contains("pages")), error.toString)
      case other => fail(s"Expected a JSON field decoding error, got: $other")
    }
  }

  @Test
  def jsonBodyUsesUtf8JsonCodecForEndpointInputAndOutput(): Unit = {
    val body: EndpointIO.Body[String, Book] = jsonBody[Book]
    val book: Book = Book("Endpoint JSON", 128, List("body"), available = false)

    body.bodyType match {
      case RawBodyType.StringBody(charset) => assertEquals(StandardCharsets.UTF_8, charset)
      case other                           => fail(s"Expected a UTF-8 string body, got: $other")
    }

    assertEquals(CodecFormat.Json(), body.codec.format)
    assertEquals(DecodeResult.Value(book), body.codec.decode(body.codec.encode(book)))
    assertTrue(body.show.contains("application/json"), body.show)
  }

  @Test
  def customCirceJsonPrinterIsUsedByJsonBodyCodec(): Unit = {
    val prettyJsonSupport: TapirJsonCirce = new TapirJsonCirce {
      override def jsonPrinter: Printer = Printer.spaces2
    }
    val body: EndpointIO.Body[String, Book] = prettyJsonSupport.jsonBody[Book]
    val book: Book = Book("Pretty Tapir", 256, List("printer", "body"), available = true)

    val encoded: String = body.codec.encode(book)

    assertEquals(CodecFormat.Json(), body.codec.format)
    assertTrue(encoded.contains("\n  \"title\""), encoded)
    assertTrue(encoded.contains("\n  \"pages\""), encoded)
    assertFalse(encoded.contains("\"title\":\"Pretty Tapir\""), encoded)
    assertEquals(DecodeResult.Value(book), body.codec.decode(encoded))
  }

  @Test
  def jsonBodyWithRawPreservesIncomingJsonAlongsideDecodedValue(): Unit = {
    val body: EndpointIO.Body[String, (String, Book)] = jsonBodyWithRaw[Book]
    val rawJson: String = """{"title":"Raw Book","pages":42,"tags":["raw"],"available":true}"""
    val expected: Book = Book("Raw Book", 42, List("raw"), available = true)

    assertEquals(DecodeResult.Value(rawJson -> expected), body.codec.decode(rawJson))
    assertEquals(body.codec.encode(rawJson -> expected), circeCodec[Book].encode(expected))
  }

  @Test
  def jsonQueryDecodesSingleJsonValueAndReportsMissingOrRepeatedValues(): Unit = {
    val queryInput: EndpointInput.Query[Search] = jsonQuery[Search]("filter")
    val search: Search = Search("tapir", 10)
    val encoded: String = circeCodec[Search].encode(search)

    assertEquals("filter", queryInput.name)
    assertEquals(CodecFormat.Json(), queryInput.codec.format)
    assertEquals(DecodeResult.Value(search), queryInput.codec.decode(List(encoded)))
    assertEquals(List(encoded), queryInput.codec.encode(search))
    assertEquals(DecodeResult.Missing, queryInput.codec.decode(Nil))
    assertEquals(DecodeResult.Multiple(List(encoded, encoded)), queryInput.codec.decode(List(encoded, encoded)))
  }

  @Test
  def optionalJsonQueryTreatsAbsentParameterAsNone(): Unit = {
    val optionalQuery: EndpointInput.Query[Option[Search]] = jsonQuery[Option[Search]]("filter")
    val search: Search = Search("native", 1)
    val encoded: String = circeCodec[Search].encode(search)

    assertTrue(optionalQuery.codec.schema.isOptional)
    assertEquals(DecodeResult.Value(None), optionalQuery.codec.decode(Nil))
    assertEquals(DecodeResult.Value(None), optionalQuery.codec.decode(List("")))
    assertEquals(DecodeResult.Value(Some(search)), optionalQuery.codec.decode(List(encoded)))
    assertEquals(List(""), optionalQuery.codec.encode(None))
  }

  @Test
  def circeJsonAndJsonObjectSchemasSupportRawJsonValues(): Unit = {
    val jsonSchema: Schema[Json] = schemaForCirceJson
    val jsonObjectSchema: Schema[JsonObject] = schemaForCirceJsonObject
    val jsonCodec: Codec.JsonCodec[Json] = circeCodec[Json]
    val jsonObjectCodec: Codec.JsonCodec[JsonObject] = circeCodec[JsonObject]
    val document: Json = Json.obj(
      "name" -> Json.fromString("tapir"),
      "enabled" -> Json.fromBoolean(true),
      "count" -> Json.fromInt(3)
    )
    val jsonObject: JsonObject = document.asObject.getOrElse(JsonObject.empty)

    assertTrue(jsonSchema.schemaType.isInstanceOf[SchemaType.SCoproduct[?]], jsonSchema.show)
    assertEquals(Some(SName("io.circe.JsonObject")), jsonObjectSchema.name)
    assertTrue(jsonObjectSchema.schemaType.isInstanceOf[SchemaType.SProduct[?]], jsonObjectSchema.show)
    assertEquals(DecodeResult.Value(document), jsonCodec.decode(jsonCodec.encode(document)))
    assertEquals(DecodeResult.Value(jsonObject), jsonObjectCodec.decode(jsonObjectCodec.encode(jsonObject)))
  }

  @Test
  def endpointsCanCombineJsonQueryJsonBodyAndJsonOutput(): Unit = {
    val api: PublicEndpoint[(Search, Book), String, Book, Any] = endpoint.post
      .in("books")
      .in(jsonQuery[Search]("filter"))
      .in(jsonBody[Book])
      .errorOut(stringBody)
      .out(jsonBody[Book])
      .name("create-book")

    val inputShow: String = api.input.show
    val outputShow: String = api.output.show

    assertEquals(Some("create-book"), api.info.name)
    assertTrue(inputShow.contains("/books"), inputShow)
    assertTrue(inputShow.contains("?filter"), inputShow)
    assertTrue(inputShow.contains("application/json"), inputShow)
    assertTrue(outputShow.contains("application/json"), outputShow)
  }
}

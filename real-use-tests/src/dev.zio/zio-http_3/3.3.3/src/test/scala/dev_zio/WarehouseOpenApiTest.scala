/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Runtime, Scope, Unsafe, ZIO}
import zio.http.*
import zio.http.codec.PathCodec.int
import zio.http.endpoint.Endpoint
import zio.http.endpoint.openapi.{OpenAPI, OpenAPIGen, SwaggerUI}
import zio.json.*
import zio.json.ast.Json
import zio.schema.{DeriveSchema, Schema}

/** Exercises ZIO HTTP OpenAPI generation and publication for a warehouse integration API. */
class WarehouseOpenApiTest {
  case class StockBalance(sku: Int, available: Int)
  object StockBalance {
    implicit val schema: Schema[StockBalance] = DeriveSchema.gen[StockBalance]
  }

  case class StockChange(quantity: Int, reason: String)
  object StockChange {
    implicit val schema: Schema[StockChange] = DeriveSchema.gen[StockChange]
  }

  @Test
  @Timeout(60)
  def publishesBothOperationsAndTheirConsumerFacingSchemas(): Unit = {
    val lookup = Endpoint(Method.GET / "stock" / int("sku"))
      .query[Boolean]("includeReserved")
      .out[StockBalance]
      .outError[String](Status.NotFound)
      .tag("warehouse")
    val adjust = Endpoint(Method.POST / "stock" / int("sku"))
      .in[StockChange]
      .out[StockBalance]
      .outError[String](Status.Conflict)
      .tag("warehouse")
    val specification: OpenAPI = OpenAPIGen.fromEndpoints("Warehouse", "1.0", lookup, adjust)
    val documentation: Routes[Any, Response] = SwaggerUI.routes("docs", specification)

    val program: ZIO[Scope, Throwable, Unit] = for {
      response <- documentation.runZIO(Request.get("/docs/Warehouse.json"))
      body <- response.body.asString
      _ <- ZIO.attempt {
        assertEquals(Status.Ok, response.status)
        assertEquals(Some("application/json"), response.rawHeader("Content-Type"))
        val document: Json = body.fromJson[Json].fold(error => throw new AssertionError(error), identity)
        assertEquals(Json.Str("Warehouse"), field(document, "info", "title"))
        assertEquals(Json.Str("1.0"), field(document, "info", "version"))
        val path: Json = field(document, "paths", "/stock/{sku}")
        val get: Json = field(path, "get")
        val post: Json = field(path, "post")
        assertTrue(elements(field(get, "tags")).contains(Json.Str("warehouse")))
        val parameters: Vector[Json] = elements(field(get, "parameters"))
        val sku: Json = parameters.find(p => field(p, "name") == Json.Str("sku")).get
        assertEquals(Json.Str("path"), field(sku, "in"))
        assertEquals(Json.Bool(true), field(sku, "required"))
        assertEquals(Json.Str("integer"), field(sku, "schema", "type"))
        val reserved: Json = parameters.find(p => field(p, "name") == Json.Str("includeReserved")).get
        assertEquals(Json.Str("query"), field(reserved, "in"))
        assertEquals(Json.Str("boolean"), field(reserved, "schema", "type"))
        val input: Json = resolveSchema(document,
          field(post, "requestBody", "content", "application/json", "schema"))
        assertEquals(Json.Str("integer"), field(input, "properties", "quantity", "type"))
        assertEquals(Json.Str("string"), field(input, "properties", "reason", "type"))
        assertTrue(elements(field(input, "required")).contains(Json.Str("reason")))
        val output: Json = resolveSchema(document,
          field(get, "responses", "200", "content", "application/json", "schema"))
        assertEquals(Json.Str("integer"), field(output, "properties", "available", "type"))
        assertEquals(
          field(get, "responses", "200", "content", "application/json", "schema"),
          field(post, "responses", "200", "content", "application/json", "schema")
        )
        assertTrue(field(get, "responses", "404").isInstanceOf[Json.Obj])
        assertTrue(field(post, "responses", "409").isInstanceOf[Json.Obj])
      }
      page <- documentation.runZIO(Request.get("/docs"))
      html <- page.body.asString
      _ <- ZIO.succeed {
        assertEquals(Status.Ok, page.status)
        assertTrue(html.contains("SwaggerUIBundle"))
        assertTrue(html.contains("/docs/Warehouse.json"))
      }
    } yield ()

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(ZIO.scoped(program)).getOrThrowFiberFailure()
    }
  }

  private def field(json: Json, names: String*): Json =
    names.foldLeft(json) { (current, name) =>
      current match {
        case Json.Obj(fields) => fields.toMap.getOrElse(name,
          throw new AssertionError(s"Missing $name in $current"))
        case _ => throw new AssertionError(s"Expected object at $name, got $current")
      }
    }

  private def elements(json: Json): Vector[Json] = json match {
    case Json.Arr(values) => values.toVector
    case _ => throw new AssertionError(s"Expected array, got $json")
  }

  private def resolveSchema(document: Json, schema: Json): Json = schema match {
    case Json.Obj(fields) => fields.toMap.get("$ref") match {
      case Some(Json.Str(reference)) => field(document, reference.stripPrefix("#/").split("/")*)
      case _ => schema
    }
    case _ => throw new AssertionError(s"Expected schema object, got $schema")
  }
}

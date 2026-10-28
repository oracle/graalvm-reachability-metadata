/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.tapircirce

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.JsonObject
import io.circe.parser.parse
import org.http4s.HttpApp
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import sttp.tapir.*
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises Circe JSON AST bodies for schemaless application settings documents. */
class SettingsJsonDocumentTest extends TapirJsonCirce {
  @Test
  @Timeout(60)
  def storesArbitraryObjectMembersAndRejectsNonObjectReplacements(): Unit = {
    val stored: Ref[IO, JsonObject] = Ref.of[IO, JsonObject](JsonObject.empty).unsafeRunSync()
    val replace = endpoint.put
      .in("settings")
      .in(jsonBody[JsonObject])
      .out(statusCode(sttp.model.StatusCode.NoContent))
      .serverLogicSuccess[IO] { document => stored.set(document) }
    val read = endpoint.get
      .in("settings")
      .out(jsonBody[Json])
      .serverLogicSuccess[IO] { _ => stored.get.map(Json.fromJsonObject) }
    val app: HttpApp[IO] = Http4sServerInterpreter[IO]().toRoutes(List(replace, read)).orNotFound

    def put(document: String): Status = {
      val request: Request[IO] = Request[IO](Method.PUT, Uri.unsafeFromString("/settings"))
        .withEntity(document)
        .putHeaders(`Content-Type`(MediaType.application.json))
      app.run(request).flatMap { response =>
        response.body.compile.drain.as(response.status)
      }.timeout(15.seconds).unsafeRunSync()
    }

    def assertDocument(expected: String): Unit = {
      val request: Request[IO] = Request[IO](Method.GET, Uri.unsafeFromString("/settings"))
      val (status, contentType, body) = (for {
        response <- app.run(request)
        text <- response.bodyText.compile.string
      } yield (response.status, response.contentType.map(_.mediaType), text))
        .timeout(15.seconds).unsafeRunSync()
      assertEquals(Status.Ok, status)
      assertEquals(Some(MediaType.application.json), contentType)
      assertEquals(parse(expected).fold(throw _, identity), parse(body).fold(throw _, identity))
    }

    val original: String = """{
      "theme": "dark",
      "plugins": {
        "vendor.example/search": {"enabled": true, "weights": [1, 0.125, 3]},
        "vendor.example/export": null
      },
      "externalAccount": 9007199254740993,
      "budget": 1234567890.123456789,
      "shortcuts": ["Ctrl+K", false, null, {"label": "Open\nsettings"}],
      "empty": {}
    }"""
    assertEquals(Status.NoContent, put(original))
    assertDocument(original)

    // Invalid replacements must leave the last accepted document available to readers.
    assertEquals(Status.BadRequest, put("""["not", "an", "object"]"""))
    assertEquals(Status.BadRequest, put("""{"theme":"""))
    assertDocument(original)

    val replacement: String = """{
      "theme": "light",
      "newPlugin": {"arbitrary-field": [[], {}, null]},
      "enabled": false
    }"""
    assertEquals(Status.NoContent, put(replacement))
    assertDocument(replacement)

    // An empty object is a valid settings reset, not a missing request body.
    assertEquals(Status.NoContent, put("{}"))
    assertDocument("{}")
  }
}

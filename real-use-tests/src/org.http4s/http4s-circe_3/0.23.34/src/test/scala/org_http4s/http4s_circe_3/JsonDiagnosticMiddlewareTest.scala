/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_circe_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import org.http4s.Header
import org.http4s.HttpApp
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe._
import org.http4s.circe.middleware.JsonDebugErrorHandler
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.ci.CIString

import java.util.Locale
import scala.concurrent.duration._

/** Exercises Circe diagnostic error middleware for a development API with credential redaction. */
class JsonDiagnosticMiddlewareTest {
  @Test
  @Timeout(60)
  def diagnoseMalformedRequestsWithoutExposingCredentials(): Unit = {
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case request @ POST -> Root / "preview" =>
        request.as[Json].flatMap(document => Ok(Json.obj("preview" -> document)))
    }
    // This diagnostic handler belongs on a development API, not a public production endpoint.
    val app: HttpApp[IO] = JsonDebugErrorHandler(routes.orNotFound)
    val request: Request[IO] = Request[IO](
      Method.POST,
      Uri.unsafeFromString("/preview?section=title&section=summary")
    ).putHeaders(
      Header.Raw(CIString("Authorization"), "Bearer development-secret"),
      Header.Raw(CIString("Cookie"), "session=private-session"),
      Header.Raw(CIString("X-Request-Id"), "preview-42")
    )
    val malformed: Request[IO] = request.withEntity("{")
      .withContentType(`Content-Type`(MediaType.application.json))
    val document: Json = Json.obj("title" -> Json.fromString("Release notes"))
    val valid: Request[IO] = request.withEntity(document)

    val result: IO[Unit] = for {
      failure <- app.run(malformed)
      diagnostic <- failure.as[Json]
      method <- IO.fromEither(diagnostic.hcursor.downField("request").get[String]("method"))
      path <- IO.fromEither(
        diagnostic.hcursor.downField("request").downField("uri").get[String]("path")
      )
      sections <- IO.fromEither(
        diagnostic.hcursor.downField("request").downField("uri").downField("query")
          .get[List[String]]("section")
      )
      headers <- IO.fromEither(
        diagnostic.hcursor.downField("request").get[List[Map[String, String]]]("headers")
      )
      message <- IO.fromEither(diagnostic.hcursor.downField("throwable").get[String]("message"))
      _ <- IO {
        assertEquals(Status.BadRequest, failure.status)
        assertEquals(Some(`Content-Type`(MediaType.application.json)), failure.contentType)
        assertEquals("POST", method)
        assertEquals("/preview", path)
        assertEquals(List("title", "summary"), sections)
        val headerValues: Map[String, String] = headers.map { header =>
          header("name").toLowerCase(Locale.ROOT) -> header("value")
        }.toMap
        assertEquals("preview-42", headerValues("x-request-id"))
        assertTrue(headerValues.contains("authorization"))
        assertTrue(headerValues.contains("cookie"))
        assertFalse(diagnostic.noSpaces.contains("development-secret"))
        assertFalse(diagnostic.noSpaces.contains("private-session"))
        assertTrue(message.startsWith("Malformed message body:"))
      }
      success <- app.run(valid)
      preview <- success.as[Json]
      _ <- IO {
        assertEquals(Status.Ok, success.status)
        assertEquals(Json.obj("preview" -> document), preview)
      }
    } yield ()

    result.timeout(30.seconds).unsafeRunSync()
  }
}

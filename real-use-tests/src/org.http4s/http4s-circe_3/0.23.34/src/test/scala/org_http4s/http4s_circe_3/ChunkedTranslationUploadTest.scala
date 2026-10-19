/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_circe_3

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import fs2.Stream
import io.circe.Json
import org.http4s.EntityDecoder
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.CirceInstances
import org.http4s.circe.jsonEncoder
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._

/** Exercises incremental single-document JSON decoding for chunked translation bundle uploads. */
class ChunkedTranslationUploadTest {
  private val codecs: CirceInstances = CirceInstances.builder.build
  private given EntityDecoder[IO, Json] = codecs.jsonDecoderIncremental[IO]

  @Test
  @Timeout(60)
  def publishOnlyCompleteUtf8TranslationDocuments(): Unit = {
    val previous: Json = Json.obj("welcome" -> Json.fromString("Welcome"))
    val translations: Json = Json.obj(
      "welcome" -> Json.fromString("Bienvenue au caf\u00e9"),
      "japanese" -> Json.fromString("\u3088\u3046\u3053\u305d"),
      "celebration" -> Json.fromString("Ready \ud83c\udf89"),
      "instructions" -> Json.fromString("Choose \"Continue\"\nThen select a language.")
    )
    val complete: String = translations.noSpaces + "\r\n  "

    val result: IO[Unit] = for {
      published <- Ref.of[IO, Json](previous)
      routes = HttpRoutes.of[IO] {
        case request @ PUT -> Root / "translations" =>
          request.decodeStrict[Json] { document =>
            published.set(document) *> NoContent()
          }
        case GET -> Root / "translations" => published.get.flatMap(Ok(_))
      }
      // An interrupted upload must leave the currently published bundle intact.
      interrupted <- routes.orNotFound.run(upload(translations.noSpaces.dropRight(1)))
      _ <- interrupted.body.compile.drain
      afterInterruption <- published.get
      _ <- IO {
        assertEquals(Status.BadRequest, interrupted.status)
        assertEquals(previous, afterInterruption)
      }
      accepted <- routes.orNotFound.run(upload(complete))
      _ <- accepted.body.compile.drain
      download <- routes.orNotFound.run(Request[IO](uri = Uri.unsafeFromString("/translations")))
      document <- download.as[Json]
      _ <- IO {
        assertEquals(Status.NoContent, accepted.status)
        assertEquals(Status.Ok, download.status)
        assertEquals(translations, document)
        assertEquals(Some(`Content-Type`(MediaType.application.json)), download.contentType)
      }
      // A second root document is not part of a valid translation bundle.
      concatenated <- routes.orNotFound.run(upload(complete + "{}"))
      _ <- concatenated.body.compile.drain
      afterRejection <- published.get
      _ <- IO {
        assertEquals(Status.BadRequest, concatenated.status)
        assertEquals(translations, afterRejection)
      }
    } yield ()

    result.timeout(30.seconds).unsafeRunSync()
  }

  private def upload(document: String): Request[IO] = {
    // Single-byte chunks deliberately split multi-byte UTF-8 characters and JSON escapes.
    val bytes: Array[Byte] = document.getBytes(StandardCharsets.UTF_8)
    val body: Stream[IO, Byte] = Stream.emits(bytes).covary[IO]
      .chunkN(1).flatMap(Stream.chunk)
    val request: Request[IO] = Request[IO](Method.PUT, Uri.unsafeFromString("/translations"))
      .withBodyStream(body)
      .withContentType(`Content-Type`(MediaType.application.json))
    assertTrue(request.contentLength.isEmpty)
    request
  }
}

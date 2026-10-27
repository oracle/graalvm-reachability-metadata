/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.http4s

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.Stream
import org.http4s.{HttpApp, HttpRoutes, Method, Request, Status, Uri}
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.middleware.{EntityLimiter, ErrorHandling}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import java.nio.charset.StandardCharsets.UTF_8

/** Exercises request entity limiting for an order-note upload endpoint. */
class LimitedNoteUploadTest {
  @Test
  @Timeout(60)
  def rejectsOversizedUploadsWithoutSavingPartialNotes(): Unit = {
    val limit: Long = 64L
    val endpoint: Uri = Uri.unsafeFromString("/orders/book-42/note")
    val boundaryNote: String = "n".repeat(limit.toInt)
    val oversizedNote: String = "n".repeat(limit.toInt + 1)
    val multibyteNote: String = "\u00e9".repeat(33)
    val finalNote: String = "Please wrap this order as a gift."

    def upload(note: String): Request[IO] =
      Request[IO](method = Method.POST, uri = endpoint).withEntity(note)

    // An unframed stream models an upload whose length is not known in advance.
    val streamedUpload: Request[IO] = Request[IO](method = Method.POST, uri = endpoint)
      .withBodyStream(Stream.emits(multibyteNote.getBytes(UTF_8).toSeq).covary[IO].chunkN(7).unchunks)

    val checks: IO[Unit] = for {
      savedNotes <- Ref.of[IO, Vector[String]](Vector.empty)
      routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
        case request @ POST -> Root / "orders" / "book-42" / "note" =>
          request.bodyText.compile.string.flatMap { note =>
            savedNotes.update(_ :+ note) *> Created(note)
          }
      }
      application: HttpApp[IO] = ErrorHandling.Custom.recoverWith(
        EntityLimiter.httpApp(routes.orNotFound, limit)
      ) {
        case EntityLimiter.EntityTooLarge(_) => PayloadTooLarge("Order notes must be at most 64 bytes")
      }
      boundary <- application.run(upload(boundaryNote))
      boundaryBody <- boundary.as[String]
      oversized <- application.run(upload(oversizedNote))
      oversizedBody <- oversized.as[String]
      streamed <- application.run(streamedUpload)
      streamedBody <- streamed.as[String]
      afterRejections <- savedNotes.get
      accepted <- application.run(upload(finalNote))
      acceptedBody <- accepted.as[String]
      allNotes <- savedNotes.get
    } yield {
      assertEquals(Status.Created, boundary.status)
      assertEquals(boundaryNote, boundaryBody)
      assertEquals(Status.PayloadTooLarge, oversized.status)
      assertEquals("Order notes must be at most 64 bytes", oversizedBody)
      assertEquals(None, streamedUpload.contentLength)
      assertEquals(Status.PayloadTooLarge, streamed.status)
      assertEquals("Order notes must be at most 64 bytes", streamedBody)
      assertEquals(Vector(boundaryNote), afterRejections)
      assertEquals(Status.Created, accepted.status)
      assertEquals(finalNote, acceptedBody)
      assertEquals(Vector(boundaryNote, finalNote), allNotes)
    }

    checks.unsafeRunSync()
  }
}

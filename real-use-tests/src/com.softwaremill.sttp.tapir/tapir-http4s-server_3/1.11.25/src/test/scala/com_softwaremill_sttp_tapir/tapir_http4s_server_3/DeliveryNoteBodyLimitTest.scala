/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_http4s_server_3

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import fs2.Stream
import org.http4s.{Header, Method, Request}
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.CIString
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.model.EndpointExtensions.*

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

/** Exercises request-body byte limits in Tapir's http4s interpreter for delivery notes. */
class DeliveryNoteBodyLimitTest {
  @Test
  @Timeout(60)
  def rejectsOversizedKnownLengthAndChunkedNotesBeforeSavingThem(): Unit = {
    val save: PublicEndpoint[String, Unit, String, Any] = endpoint.post
      .in("shipments" / "parcel-42" / "notes")
      .in(stringBody)
      .out(statusCode(StatusCode.Created))
      .out(stringBody)
      .maxRequestBodyLength(64L)
    val shortNote: String = "Please ring the doorbell."
    val boundaryNote: String = "Leave the parcel with reception; call the recipient upon arrival"
    val oversizedNote: String = boundaryNote + "!"
    val multibyteNote: String = "\u00e9" * 33

    val scenario: IO[Unit] = for {
      notes <- Ref.of[IO, Vector[String]](Vector.empty)
      app = Http4sServerInterpreter[IO]().toRoutes(
        save.serverLogicSuccess[IO] { (note: String) =>
          notes.modify { current =>
            (current :+ note, s"note-${current.size + 1}")
          }
        }
      ).orNotFound
      _ <- IO {
        assertEquals(64, boundaryNote.getBytes(StandardCharsets.UTF_8).length)
        assertTrue(multibyteNote.length < 64)
      }
      accepted <- app.run(noteRequest(shortNote))
      receipt <- accepted.as[String]
      boundary <- app.run(noteRequest(boundaryNote))
      boundaryReceipt <- boundary.as[String]
      _ <- IO {
        assertEquals(201, accepted.status.code)
        assertEquals("note-1", receipt)
        assertEquals(201, boundary.status.code)
        assertEquals("note-2", boundaryReceipt)
      }
      knownLength <- app.run(noteRequest(oversizedNote))
      _ <- knownLength.body.compile.drain
      // A sender may omit Content-Length; enforce the limit while reading its chunks too.
      chunkedRequest = Request[IO](Method.POST, uri"/shipments/parcel-42/notes")
        .withBodyStream(Stream.emits(multibyteNote.getBytes(StandardCharsets.UTF_8))
          .covary[IO].chunkN(7).unchunks)
        .putHeaders(Header.Raw(CIString("Content-Type"), "text/plain; charset=UTF-8"))
      chunked <- app.run(chunkedRequest)
      _ <- chunked.body.compile.drain
      afterRejection <- notes.get
      _ <- IO {
        assertEquals(413, knownLength.status.code)
        assertEquals(413, chunked.status.code)
        assertEquals(Vector(shortNote, boundaryNote), afterRejection)
      }
      retried <- app.run(noteRequest("Collect from reception."))
      retryReceipt <- retried.as[String]
      saved <- notes.get
      _ <- IO {
        assertEquals(201, retried.status.code)
        assertEquals("note-3", retryReceipt)
        assertEquals(Vector(shortNote, boundaryNote, "Collect from reception."), saved)
      }
    } yield ()

    scenario.timeout(45.seconds).unsafeRunSync()
  }

  private def noteRequest(note: String): Request[IO] =
    Request[IO](Method.POST, uri"/shipments/parcel-42/notes")
      .withEntity(note)
      .putHeaders(Header.Raw(CIString("Content-Type"), "text/plain; charset=UTF-8"))
}

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
import org.http4s.multipart.{Boundary, Multipart, Part as HttpPart}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.CIString
import sttp.model.{Part, StatusCode}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises multipart attachment uploads through Tapir's http4s interpreter. */
class MultipartClaimAttachmentTest {
  final case class ClaimUpload(description: String, attachment: Part[Array[Byte]])
  final case class SavedAttachment(
      description: String,
      filename: Option[String],
      contentType: Option[String],
      bytes: Vector[Byte]
  )

  @Test
  @Timeout(60)
  def acceptsABinaryClaimAttachmentAndRejectsAnIncompleteForm(): Unit = {
    val payload: Vector[Byte] = Vector(0, 1, 13, 10, 127, -128, -1, 42)
    val upload: PublicEndpoint[ClaimUpload, Unit, String, Any] = endpoint.post
      .in("claims" / "attachments")
      .in(multipartBody[ClaimUpload])
      .out(statusCode(StatusCode.Created))
      .out(stringBody)

    val scenario: IO[Unit] = for {
      saved <- Ref.of[IO, Vector[SavedAttachment]](Vector.empty)
      app = Http4sServerInterpreter[IO]().toRoutes(
        upload.serverLogicSuccess { (form: ClaimUpload) =>
          val record: SavedAttachment = SavedAttachment(
            form.description,
            form.attachment.fileName,
            form.attachment.contentType,
            form.attachment.body.toVector
          )
          saved.update(_ :+ record).as("attachment-1")
        }
      ).orNotFound
      complete = Multipart[IO](Vector(
        HttpPart.formData[IO]("description", "Receipt for claim 42"),
        HttpPart.fileData[IO](
          "attachment",
          "receipt.bin",
          Stream.emits(payload).covary[IO],
          Header.Raw(CIString("Content-Type"), "application/octet-stream")
        )
      ), Boundary("claim-upload-boundary"))
      accepted <- app.run(uploadRequest(complete))
      acceptedBody <- accepted.as[String]
      records <- saved.get
      _ <- IO {
        assertEquals(201, accepted.status.code)
        assertEquals("attachment-1", acceptedBody)
        assertEquals(Vector(SavedAttachment(
          "Receipt for claim 42",
          Some("receipt.bin"),
          Some("application/octet-stream"),
          payload
        )), records)
      }
      incomplete = Multipart[IO](
        Vector(HttpPart.formData[IO]("description", "Missing receipt")),
        Boundary("claim-incomplete-boundary")
      )
      rejected <- app.run(uploadRequest(incomplete))
      rejectionBody <- rejected.as[String]
      afterRejected <- saved.get
      _ <- IO {
        assertEquals(400, rejected.status.code)
        assertTrue(rejectionBody.nonEmpty)
        assertEquals(records, afterRejected)
      }
    } yield ()

    scenario.timeout(45.seconds).unsafeRunSync()
  }

  private def uploadRequest(form: Multipart[IO]): Request[IO] =
    Request[IO](Method.POST, uri"/claims/attachments")
      .withEntity(form)
      .withHeaders(form.headers)
}

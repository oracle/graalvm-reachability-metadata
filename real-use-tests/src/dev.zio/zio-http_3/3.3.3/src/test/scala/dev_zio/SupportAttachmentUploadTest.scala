/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio

import java.nio.file.{Files, Path}

import org.junit.jupiter.api.Assertions.{assertArrayEquals, assertEquals}
import org.junit.jupiter.api.{Test, Timeout}
import org.junit.jupiter.api.io.TempDir
import zio.{Chunk, Runtime, Unsafe, ZIO}
import zio.http.*
import zio.stream.ZStream

/** Exercises ZIO HTTP multipart form uploads for support-ticket attachments. */
class SupportAttachmentUploadTest {
  @Test
  @Timeout(60)
  def storesUploadedAttachmentWithItsTicketDetails(@TempDir directory: Path): Unit = {
    val attachment: Chunk[Byte] = Chunk.fromArray(
      Array[Byte](0, 1, 2, 13, 10, 45, 45, 127, -128, -1, 42, 0)
    )
    val form: Form = Form(
      FormField.simpleField("ticket", "SUP-1042"),
      FormField.textField("description", "Device log & diagnostic data"),
      FormField.streamingBinaryField(
        "attachment",
        ZStream.fromChunk(attachment).rechunk(3),
        MediaType.application.`octet-stream`,
        filename = Some("diagnostics.bin")
      )
    )

    val program: ZIO[Any, Throwable, Unit] = for {
      body <- Body.fromMultipartFormUUID(form)
      response <- receiveUpload(Request.post("/support/attachments", body), directory)
      receipt <- response.body.asString
      stored <- ZIO.attemptBlocking(Files.readAllBytes(directory.resolve("SUP-1042.bin")))
      _ <- ZIO.succeed {
        assertEquals(Status.Created, response.status)
        assertEquals(
          "SUP-1042: diagnostics.bin; Device log & diagnostic data; 12 bytes",
          receipt
        )
        assertArrayEquals(attachment.toArray, stored)
      }
    } yield ()

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(program).getOrThrowFiberFailure()
    }
  }

  private def receiveUpload(request: Request, directory: Path): ZIO[Any, Throwable, Response] =
    for {
      form <- request.body.asMultipartForm
      ticket <- requiredField(form, "ticket").flatMap(_.asText)
      description <- requiredField(form, "description").flatMap(_.asText)
      attachment <- requiredField(form, "attachment")
      filename <- ZIO.fromOption(attachment.filename)
        .orElseFail(new IllegalArgumentException("Attachment filename is required"))
      _ <- ZIO.fail(new IllegalArgumentException("Unsupported attachment type"))
        .unless(attachment.contentType == MediaType.application.`octet-stream`)
      _ <- ZIO.fail(new IllegalArgumentException("Invalid ticket identifier"))
        .unless(ticket.matches("SUP-[0-9]+"))
      bytes <- attachment.asChunk
      _ <- ZIO.attemptBlocking(Files.write(directory.resolve(s"$ticket.bin"), bytes.toArray))
    } yield Response.text(s"$ticket: $filename; $description; ${bytes.size} bytes")
      .status(Status.Created)

  private def requiredField(form: Form, name: String): ZIO[Any, Throwable, FormField] =
    ZIO.fromOption(form.get(name))
      .orElseFail(new IllegalArgumentException(s"Missing form field: $name"))
}

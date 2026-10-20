/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_server_3

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

import org.junit.jupiter.api.Assertions.{assertArrayEquals, assertEquals}
import org.junit.jupiter.api.{Test, Timeout}
import sttp.model.{Part, StatusCode}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.jdkhttp.JdkHttpServer

/** Exercises multipart request-body interpretation for support-ticket attachment uploads. */
class TicketAttachmentUploadTest {
  private case class Upload(ticket: String, attachment: Part[Array[Byte]])
  private case class SavedAttachment(ticket: String, filename: String, bytes: Array[Byte])

  @Test
  @Timeout(60)
  def acceptsNamedFilePartsWithoutCorruptingTheAttachment(): Unit = {
    val saved: AtomicReference[Option[SavedAttachment]] = new AtomicReference(None)
    val upload: ServerEndpoint[Any, Identity] = endpoint.post
      .in("support" / "attachments")
      .in(multipartBody[Upload])
      .out(statusCode(StatusCode.Created).and(stringBody))
      .serverLogicSuccess[Identity] { form =>
        val filename: String = form.attachment.fileName.getOrElse("attachment.bin")
        saved.set(Some(SavedAttachment(form.ticket, filename, form.attachment.body)))
        s"Attached $filename to ${form.ticket}: ${form.attachment.body.length} bytes"
      }
    val download: ServerEndpoint[Any, Identity] = endpoint.get
      .in("support" / "attachments")
      .errorOut(statusCode(StatusCode.NotFound).and(stringBody))
      .out(header[String]("X-Ticket").and(header[String]("X-Filename")).and(byteArrayBody))
      .serverLogic[Identity] { _ =>
        saved.get().toRight("No attachment").map(file => (file.ticket, file.filename, file.bytes))
      }

    val server = JdkHttpServer().host("127.0.0.1").port(0)
      .addEndpoint(upload).addEndpoint(download).start()
    try {
      val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
      try {
        val uri: URI = URI.create(s"http://127.0.0.1:${server.getAddress.getPort}/support/attachments")
        val boundary: String = "ticket-attachment-boundary"
        val content: Array[Byte] = Array[Byte](0, 1, 2, 10, 13, 34, 65, 90, 127, -128, -1)

        def submit(includeTicket: Boolean): HttpResponse[String] = {
          // Send the file first: form binding must use part names, not their wire order.
          val fileHeader: String = s"--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"attachment\"; filename=diagnostics.bin\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n"
          val ticketPart: String = if (includeTicket) {
            s"--$boundary\r\n" +
              "Content-Disposition: form-data; name=\"ticket\"\r\n\r\nSUP-42\r\n"
          } else ""
          val bytes: Array[Byte] = fileHeader.getBytes(StandardCharsets.US_ASCII) ++ content ++
            ("\r\n" + ticketPart + s"--$boundary--\r\n").getBytes(StandardCharsets.US_ASCII)
          val request: HttpRequest = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
            .header("Content-Type", s"multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        def fetch(): HttpResponse[Array[Byte]] = {
          val request: HttpRequest = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build()
          client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        }

        assertEquals(404, fetch().statusCode())
        val accepted: HttpResponse[String] = submit(includeTicket = true)
        assertEquals(201, accepted.statusCode())
        assertEquals("Attached diagnostics.bin to SUP-42: 11 bytes", accepted.body())
        val attachment: HttpResponse[Array[Byte]] = fetch()
        assertEquals(200, attachment.statusCode())
        assertEquals("SUP-42", attachment.headers().firstValue("X-Ticket").orElse(""))
        assertEquals("diagnostics.bin", attachment.headers().firstValue("X-Filename").orElse(""))
        assertArrayEquals(content, attachment.body())

        // A missing required text part must not invoke application logic and replace the saved file.
        val incomplete: HttpResponse[String] = submit(includeTicket = false)
        assertEquals(400, incomplete.statusCode())
        val retained: HttpResponse[Array[Byte]] = fetch()
        assertEquals(200, retained.statusCode())
        assertEquals("SUP-42", retained.headers().firstValue("X-Ticket").orElse(""))
        assertEquals("diagnostics.bin", retained.headers().firstValue("X-Filename").orElse(""))
        assertArrayEquals(content, retained.body())
      } finally client.close()
    } finally server.stop(0)
  }
}

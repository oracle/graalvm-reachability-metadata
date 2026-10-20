/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.core_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import scala.concurrent.duration.*
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.StatusCode

/** Exercises multipart request uploads by attaching a binary diagnostic file to a support ticket. */
class SupportAttachmentUploadTest {
  @Test
  @Timeout(60)
  def submitsTicketDescriptionAndDiagnosticAttachment(): Unit = {
    val attachment: Path = Files.createTempFile("sttp-diagnostics-", ".bin")
    val diagnosticBytes: Array[Byte] = Array.tabulate[Byte](8192)(index => (index % 256).toByte)
    val description: String = "Printer in caf\u00e9 stops after two pages"
    val uploads: LinkedBlockingQueue[ReceivedUpload] = new LinkedBlockingQueue[ReceivedUpload]()
    val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    try {
      Files.write(attachment, diagnosticBytes)
      server.createContext("/tickets/T17/attachments", (exchange: HttpExchange) => {
        try {
          uploads.add(ReceivedUpload(
            exchange.getRequestMethod,
            exchange.getRequestHeaders.getFirst("Content-Type"),
            exchange.getRequestBody.readAllBytes()
          ))
          val receipt: Array[Byte] = "Attachment diagnostics.bin stored for ticket T17".getBytes(UTF_8)
          exchange.getResponseHeaders.set("Content-Type", "text/plain; charset=UTF-8")
          exchange.getResponseHeaders.set("Location", "/tickets/T17/attachments/A1")
          exchange.sendResponseHeaders(201, receipt.length.toLong)
          exchange.getResponseBody.write(receipt)
        } finally exchange.close()
      })
      server.start()

      val backend: WebSocketSyncBackend = HttpClientSyncBackend(BackendOptions.connectionTimeout(10.seconds))
      try {
        val port: Int = server.getAddress.getPort
        val response: Response[Either[String, String]] = basicRequest
          .multipartBody(
            multipart("description", description),
            multipartFile("attachment", attachment)
              .fileName("diagnostics.bin")
              .contentType("application/octet-stream")
          )
          .post(uri"http://127.0.0.1:$port/tickets/T17/attachments")
          .readTimeout(20.seconds)
          .send(backend)

        assertEquals(StatusCode.Created, response.code)
        assertEquals(Right("Attachment diagnostics.bin stored for ticket T17"), response.body)
        assertEquals(Some("/tickets/T17/attachments/A1"), response.header("Location"))

        val upload: ReceivedUpload = uploads.poll(10, TimeUnit.SECONDS)
        assertNotNull(upload, "The ticket service must receive the upload")
        assertEquals("POST", upload.method)
        assertTrue(upload.contentType.startsWith("multipart/form-data;"))
        val boundary: String = upload.contentType.split(";").iterator.map(_.trim)
          .find(_.startsWith("boundary=")).get.stripPrefix("boundary=").stripPrefix("\"").stripSuffix("\"")
        val wireBody: String = new String(upload.body, ISO_8859_1)
        assertTrue(wireBody.startsWith(s"--$boundary\r\n"))
        assertTrue(wireBody.stripSuffix("\r\n").endsWith(s"--$boundary--"))
        val parts: Array[String] = wireBody.split(Pattern.quote(s"--$boundary"), -1)
          .drop(1).dropRight(1).map(_.stripPrefix("\r\n").stripSuffix("\r\n"))
        assertEquals(2, parts.length)

        val textPart: Array[String] = parts(0).split("\r\n\r\n", 2)
        assertTrue(textPart(0).toLowerCase(Locale.ROOT)
          .contains("content-disposition: form-data; name=\"description\""))
        assertEquals(description, new String(textPart(1).getBytes(ISO_8859_1), UTF_8))

        val filePart: Array[String] = parts(1).split("\r\n\r\n", 2)
        val fileHeaders: String = filePart(0).toLowerCase(Locale.ROOT)
        assertTrue(fileHeaders.contains("content-disposition: form-data; name=\"attachment\""))
        assertTrue(fileHeaders.contains("filename=\"diagnostics.bin\""))
        assertTrue(fileHeaders.contains("content-type: application/octet-stream"))
        assertArrayEquals(diagnosticBytes, filePart(1).getBytes(ISO_8859_1))
      } finally backend.close()
    } finally {
      server.stop(0)
      Files.deleteIfExists(attachment)
    }
  }

  private case class ReceivedUpload(method: String, contentType: String, body: Array[Byte])
}

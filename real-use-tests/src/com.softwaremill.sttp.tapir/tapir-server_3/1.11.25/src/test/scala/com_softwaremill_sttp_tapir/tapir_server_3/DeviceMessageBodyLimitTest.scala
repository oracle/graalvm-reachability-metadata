/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_server_3

import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.jdkhttp.JdkHttpServer
import sttp.tapir.server.model.EndpointExtensions.*

/** Exercises server request-body size enforcement for a device's saved display message. */
class DeviceMessageBodyLimitTest {
  @Test
  @Timeout(60)
  def rejectsOversizedMessagesWithoutReplacingTheSavedDisplay(): Unit = {
    val savedMessage: AtomicReference[String] = new AtomicReference("Welcome")
    val updateMessage: ServerEndpoint[Any, Identity] = endpoint.put
      .in("device" / "message")
      .in(stringBody)
      .maxRequestBodyLength(128L)
      .out(stringBody)
      .serverLogicSuccess[Identity] { message =>
        savedMessage.set(message)
        "Display updated"
      }
    val readMessage: ServerEndpoint[Any, Identity] = endpoint.get
      .in("device" / "message")
      .out(stringBody)
      .serverLogicSuccess[Identity](_ => savedMessage.get())

    val server = JdkHttpServer().host("127.0.0.1").port(0)
      .addEndpoint(updateMessage).addEndpoint(readMessage).start()
    try {
      val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
      try {
        val uri: URI = URI.create(s"http://127.0.0.1:${server.getAddress.getPort}/device/message")

        def update(message: String, chunked: Boolean = false): HttpResponse[String] = {
          val bytes: Array[Byte] = message.getBytes(StandardCharsets.UTF_8)
          val publisher: HttpRequest.BodyPublisher = if (chunked) {
            // Unknown content length: the server must enforce the limit while reading the body.
            HttpRequest.BodyPublishers.ofInputStream(() => new ByteArrayInputStream(bytes))
          } else HttpRequest.BodyPublishers.ofByteArray(bytes)
          val request: HttpRequest = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
            .header("Content-Type", "text/plain; charset=UTF-8").PUT(publisher).build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        def readDisplay(): String = {
          val request: HttpRequest = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build()
          val response: HttpResponse[String] = client.send(request, HttpResponse.BodyHandlers.ofString())
          assertEquals(200, response.statusCode())
          response.body()
        }

        val initial: HttpResponse[String] = update("Store opens at nine")
        assertEquals(200, initial.statusCode())
        assertEquals("Display updated", initial.body())
        assertEquals("Store opens at nine", readDisplay())

        val tooLarge: HttpResponse[String] = update("x" * 129)
        assertEquals(413, tooLarge.statusCode())
        assertEquals("Store opens at nine", readDisplay())

        val chunkedTooLarge: HttpResponse[String] = update("y" * 129, chunked = true)
        assertEquals(413, chunkedTooLarge.statusCode())
        assertEquals("Store opens at nine", readDisplay())

        // These messages have fewer than 128 characters, but the limit is in UTF-8 bytes.
        val exactLimit: String = "\u00e9" * 64
        val accepted: HttpResponse[String] = update(exactLimit, chunked = true)
        assertEquals(200, accepted.statusCode())
        assertEquals("Display updated", accepted.body())
        assertEquals(exactLimit, readDisplay())

        val multibyteTooLarge: HttpResponse[String] = update("\u00e9" * 65)
        assertEquals(413, multibyteTooLarge.statusCode())
        assertEquals(exactLimit, readDisplay())

        // A rejected request must not prevent a later valid update.
        val recovered: HttpResponse[String] = update("Closed for the day")
        assertEquals(200, recovered.statusCode())
        assertEquals("Closed for the day", readDisplay())
      } finally client.close()
    } finally server.stop(0)
  }
}

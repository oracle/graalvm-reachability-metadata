/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_server_3

import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import play.api.Mode
import play.api.mvc.Results.Created
import play.api.mvc.Results.Ok
import play.core.server.Server
import play.core.server.ServerConfig

/** Exercises chunked request-body decoding and upload-size enforcement for supplier CSV imports. */
class ChunkedSupplierImportTest {
  @Test
  @Timeout(60)
  def importsAnUnknownLengthStreamWithoutCommittingAnOversizedReplacement(): Unit = {
    val imported: AtomicReference[String] = new AtomicReference[String]("No supplier prices imported")
    val config: ServerConfig = ServerConfig(
      port = Some(0),
      address = "127.0.0.1",
      mode = Mode.Test
    )

    Server.withRouterFromComponents(config) { components =>
      {
        case request if request.method == "POST" && request.path == "/supplier/prices" =>
          components.defaultActionBuilder(components.playBodyParsers.text(maxLength = 128)) { incoming =>
            val rows: List[String] = incoming.body.linesIterator.drop(1).filter(_.nonEmpty).toList
            val summary: String = s"Imported ${rows.size} prices: ${rows.mkString(";")}"
            imported.set(summary)
            Created(summary)
          }
        case request if request.method == "GET" && request.path == "/supplier/prices" =>
          components.defaultActionBuilder { _ => Ok(imported.get()) }
      }
    } { port =>
      val client: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .build()
      try {
        val endpoint: URI = URI.create(s"http://127.0.0.1:${port.value}/supplier/prices")
        def upload(csv: String): HttpResponse[String] = {
          val bytes: Array[Byte] = csv.getBytes(StandardCharsets.UTF_8)
          // An input-stream publisher has no content length, so HTTP/1.1 uses chunked framing.
          val request: HttpRequest = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "text/plain; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofInputStream(() => new ByteArrayInputStream(bytes)))
            .build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }
        def currentPrices(): HttpResponse[String] = {
          val request: HttpRequest = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        val accepted: HttpResponse[String] = upload("sku,price\nnotebook,12.50\npencil,0.75\n")
        val expected: String = "Imported 2 prices: notebook,12.50;pencil,0.75"
        assertEquals(201, accepted.statusCode())
        assertEquals(expected, accepted.body())
        val beforeRejection: HttpResponse[String] = currentPrices()
        assertEquals(200, beforeRejection.statusCode())
        assertEquals(expected, beforeRejection.body())

        // There is no Content-Length to reject up front: the parser must enforce its streaming limit.
        val oversized: HttpResponse[String] = upload("sku,price\n" + "replacement,99.99\n" * 20)
        assertEquals(413, oversized.statusCode())
        val afterRejection: HttpResponse[String] = currentPrices()
        assertEquals(200, afterRejection.statusCode())
        assertEquals(expected, afterRejection.body())

        val retry: HttpResponse[String] = upload("sku,price\nnotebook,11.00\n")
        assertEquals(201, retry.statusCode())
        assertEquals("Imported 1 prices: notebook,11.00", retry.body())
        val afterRetry: HttpResponse[String] = currentPrices()
        assertEquals(200, afterRetry.statusCode())
        assertEquals(retry.body(), afterRetry.body())
      } finally {
        client.close()
      }
    }
  }
}

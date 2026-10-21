/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_server_3

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import play.api.Mode
import play.api.mvc.Results.Created
import play.core.server.Server
import play.core.server.ServerConfig

/** Exercises server-level HTTP request-header limits for warehouse scanner submissions. */
class WarehouseHeaderLimitTest {
  @Test
  @Timeout(60)
  def rejectsOversizedScannerContextWithoutQueuingThePick(): Unit = {
    val properties: Properties = new Properties()
    properties.setProperty("play.server.max-header-size", "512b")
    val config: ServerConfig = ServerConfig(
      port = Some(0),
      address = "127.0.0.1",
      mode = Mode.Test,
      properties = properties
    )
    val acceptedPicks: AtomicInteger = new AtomicInteger()

    Server.withRouterFromComponents(config) { components =>
      {
        case request if request.method == "POST" && request.path == "/warehouse/picks" =>
          components.defaultActionBuilder(components.playBodyParsers.text) { incoming =>
            val scanner: String = incoming.headers.get("X-Scanner-Context").getOrElse("unknown")
            val sequence: Int = acceptedPicks.incrementAndGet()
            Created(s"Queued ${incoming.body} for $scanner (#$sequence)")
          }
      }
    } { port =>
      val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .version(HttpClient.Version.HTTP_1_1)
        .build()
      try {
        val endpoint: URI = URI.create(s"http://127.0.0.1:${port.value}/warehouse/picks")
        def submit(pick: String, scanner: String): HttpResponse[String] = {
          val request: HttpRequest = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "text/plain; charset=UTF-8")
            .header("X-Scanner-Context", scanner)
            .POST(HttpRequest.BodyPublishers.ofString(pick))
            .build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        val first: HttpResponse[String] = submit("PICK-17", "scanner-7")
        assertEquals(201, first.statusCode())
        assertEquals("Queued PICK-17 for scanner-7 (#1)", first.body())

        // A misconfigured scanner must not enqueue work with an oversized context header.
        val rejected: HttpResponse[String] = submit("PICK-18", "x" * 1024)
        assertEquals(431, rejected.statusCode())

        // Rejection must not take the listener down or consume a pick sequence number.
        val next: HttpResponse[String] = submit("PICK-19", "scanner-8")
        assertEquals(201, next.statusCode())
        assertEquals("Queued PICK-19 for scanner-8 (#2)", next.body())
      } finally {
        client.close()
      }
    }
  }
}

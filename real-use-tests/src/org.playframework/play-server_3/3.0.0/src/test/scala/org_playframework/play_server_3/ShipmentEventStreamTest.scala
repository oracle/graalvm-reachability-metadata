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
import org.apache.pekko.stream.scaladsl.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import play.api.Mode
import play.api.libs.EventSource.Event
import play.api.mvc.Results.Ok
import play.core.server.Server
import play.core.server.ServerConfig

/** Exercises chunked HTTP response streaming with a resumable server-sent shipment event feed. */
class ShipmentEventStreamTest {
  @Test
  @Timeout(60)
  def streamsShipmentEventsAndResumesAfterTheLastReceivedEvent(): Unit = {
    val config: ServerConfig = ServerConfig(
      port = Some(0),
      address = "127.0.0.1",
      mode = Mode.Test
    )
    val events: List[Event] = List(
      Event("shipment=parcel-42\nstatus=packed", Some("101"), Some("shipment")),
      Event("shipment=parcel-42\nstatus=dispatched", Some("102"), Some("shipment"))
    )

    Server.withRouterFromComponents(config) { components =>
      {
        case request if request.method == "GET" && request.path == "/shipments/events" =>
          components.defaultActionBuilder { incoming =>
            val lastSeen: Int = incoming.headers.get("Last-Event-ID").fold(0)(_.toInt)
            val pending: List[Event] = events.filter(_.id.exists(_.toInt > lastSeen))
            Ok.chunked(Source(pending)).as("text/event-stream")
              .withHeaders("Cache-Control" -> "no-cache")
          }
      }
    } { port =>
      val client: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .build()
      try {
        def fetch(lastEventId: Option[String]): HttpResponse[String] = {
          val builder: HttpRequest.Builder = HttpRequest.newBuilder()
            .uri(URI.create(s"http://127.0.0.1:${port.value}/shipments/events"))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "text/event-stream")
            .GET()
          lastEventId.foreach(id => builder.header("Last-Event-ID", id))
          client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        }

        // Read through stream completion, rather than depending on chunk boundaries or timing.
        val full: HttpResponse[String] = fetch(None)
        assertEquals(200, full.statusCode())
        assertEquals("text/event-stream", full.headers().firstValue("Content-Type").orElseThrow())
        assertEquals("no-cache", full.headers().firstValue("Cache-Control").orElseThrow())
        assertEquals("chunked", full.headers().firstValue("Transfer-Encoding").orElseThrow())
        assertFalse(full.headers().firstValue("Content-Length").isPresent())
        val expected: String = "event: shipment\nid: 101\ndata: shipment=parcel-42\ndata: status=packed\n\n" +
          "event: shipment\nid: 102\ndata: shipment=parcel-42\ndata: status=dispatched\n\n"
        assertEquals(expected, full.body())

        val resumed: HttpResponse[String] = fetch(Some("101"))
        assertEquals(200, resumed.statusCode())
        assertEquals(
          "event: shipment\nid: 102\ndata: shipment=parcel-42\ndata: status=dispatched\n\n",
          resumed.body()
        )
      } finally {
        client.close()
      }
    }
  }
}

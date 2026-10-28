/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_server_3

import java.io.BufferedWriter
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import play.api.Mode
import play.api.mvc.Results.Accepted
import play.core.server.Server
import play.core.server.ServerConfig
import scala.concurrent.Future

/** Exercises server-managed application shutdown hooks that flush a buffered audit journal. */
class AuditJournalShutdownTest {
  @Test
  @Timeout(60)
  def flushesAcceptedAuditEventsWhenTheServerStops(): Unit = {
    val path: Path = Files.createTempFile("audit-journal-", ".log")
    try {
      val journal: AuditJournal = new AuditJournal(path)
      try {
        val config: ServerConfig = ServerConfig(
          port = Some(0),
          address = "127.0.0.1",
          mode = Mode.Test
        )

        Server.withRouterFromComponents(config) { components =>
          components.applicationLifecycle.addStopHook { () =>
            Future {
              journal.close()
            }(using components.executionContext)
          }
          {
            case request if request.method == "POST" && request.path == "/audit/events" =>
              components.defaultActionBuilder(components.playBodyParsers.text) { incoming =>
                journal.record(incoming.body)
                Accepted("Event queued for audit")
              }
          }
        } { port =>
          val client: HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
          try {
            val endpoint: URI = URI.create(s"http://127.0.0.1:${port.value}/audit/events")
            val events: List[String] = List(
              "shipment=parcel-42;action=packed",
              "shipment=parcel-42;action=dispatched"
            )
            events.foreach { event =>
              val request: HttpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "text/plain; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(event))
                .build()
              val response: HttpResponse[String] = client.send(request, HttpResponse.BodyHandlers.ofString())
              assertEquals(202, response.statusCode())
              assertEquals("Event queued for audit", response.body())
            }
          } finally {
            client.close()
          }
        }

        // Server shutdown must finish the asynchronous stop hook before returning.
        assertEquals(
          "shipment=parcel-42;action=packed\nshipment=parcel-42;action=dispatched\n",
          Files.readString(path)
        )
      } finally {
        // Also release the journal if application startup or an assertion fails.
        journal.close()
      }
    } finally {
      Files.deleteIfExists(path)
    }
  }

  private class AuditJournal(path: Path) extends AutoCloseable {
    private val writer: BufferedWriter = Files.newBufferedWriter(path)

    def record(event: String): Unit = synchronized {
      writer.write(event)
      writer.write("\n")
    }

    override def close(): Unit = synchronized {
      writer.close()
    }
  }
}

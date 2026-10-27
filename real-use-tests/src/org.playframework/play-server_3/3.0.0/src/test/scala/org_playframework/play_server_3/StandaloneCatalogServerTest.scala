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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import play.api.Mode
import play.api.mvc.Results.Ok
import play.core.server.Server
import play.core.server.ServerConfig

/** Exercises standalone HTTP serving with component-based application routes. */
class StandaloneCatalogServerTest {
  @Test
  @Timeout(60)
  def servesCatalogLookupOverHttp(): Unit = {
    val config: ServerConfig = ServerConfig(
      port = Some(0),
      address = "127.0.0.1",
      mode = Mode.Test
    )

    Server.withRouterFromComponents(config) { components =>
      {
        case request if request.method == "GET" && request.path == "/catalog" =>
          components.defaultActionBuilder { incoming =>
            val category: String = incoming.getQueryString("category").getOrElse("all")
            Ok(s"Catalog: $category\nAvailable: notebook, pencil\n")
              .withHeaders("X-Catalog-Source" -> "warehouse")
          }
      }
    } { port =>
      val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
      try {
        val request: HttpRequest = HttpRequest.newBuilder()
          .uri(URI.create(s"http://127.0.0.1:${port.value}/catalog?category=office%20supplies"))
          .timeout(Duration.ofSeconds(20))
          .GET()
          .build()
        val response: HttpResponse[String] = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertEquals("Catalog: office supplies\nAvailable: notebook, pencil\n", response.body())
        assertEquals("warehouse", response.headers().firstValue("X-Catalog-Source").orElseThrow())
        assertEquals("text/plain; charset=UTF-8", response.headers().firstValue("Content-Type").orElseThrow())
      } finally {
        client.close()
      }
    }
  }
}

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
import play.api.mvc.Results.Created
import play.core.server.Server
import play.core.server.ServerConfig

/** Exercises trusted reverse-proxy forwarding of client addresses and HTTPS for order receipts. */
class ProxiedOrderReceiptTest {
  @Test
  @Timeout(60)
  def createsPublicReceiptLinksWithoutTrustingClientSuppliedAddressPrefixes(): Unit = {
    val config: ServerConfig = ServerConfig(
      port = Some(0),
      address = "127.0.0.1",
      mode = Mode.Test
    )

    // Play trusts loopback proxies by default; the HTTP client is the local ingress proxy here.
    Server.withRouterFromComponents(config) { components =>
      {
        case request if request.method == "POST" && request.path == "/orders" =>
          components.defaultActionBuilder(components.playBodyParsers.text) { incoming =>
            val scheme: String = if (incoming.secure) "https" else "http"
            Created(s"Order ${incoming.body} accepted from ${incoming.remoteAddress}")
              .withHeaders("Location" -> s"$scheme://orders.example.test/receipts/${incoming.body}")
          }
      }
    } { port =>
      val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
      try {
        val endpoint: URI = URI.create(s"http://127.0.0.1:${port.value}/orders")
        def placeOrder(order: String, forwarding: Option[(String, String)]): HttpResponse[String] = {
          val builder: HttpRequest.Builder = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "text/plain; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(order))
          forwarding.foreach { (addresses, protocols) =>
            builder.header("X-Forwarded-For", addresses)
            builder.header("X-Forwarded-Proto", protocols)
          }
          client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        }

        val direct: HttpResponse[String] = placeOrder("PO-41", None)
        assertEquals(201, direct.statusCode())
        assertEquals("Order PO-41 accepted from 127.0.0.1", direct.body())
        assertEquals("http://orders.example.test/receipts/PO-41",
          direct.headers().firstValue("Location").orElseThrow())

        // TLS terminates at the proxy, so the application receives plain HTTP plus forwarding headers.
        val proxied: HttpResponse[String] = placeOrder("PO-42", Some("198.51.100.23" -> "https"))
        assertEquals(201, proxied.statusCode())
        assertEquals("Order PO-42 accepted from 198.51.100.23", proxied.body())
        assertEquals("https://orders.example.test/receipts/PO-42",
          proxied.headers().firstValue("Location").orElseThrow())

        // The ingress appends the actual peer and protocol after the client's supplied entries.
        // Stop at the first untrusted hop, not at the attacker's leftmost address or protocol.
        val spoofed: HttpResponse[String] = placeOrder(
          "PO-43",
          Some("203.0.113.250, 198.51.100.23" -> "http, https")
        )
        assertEquals(201, spoofed.statusCode())
        assertEquals("Order PO-43 accepted from 198.51.100.23", spoofed.body())
        assertEquals("https://orders.example.test/receipts/PO-43",
          spoofed.headers().firstValue("Location").orElseThrow())
      } finally {
        client.close()
      }
    }
  }
}

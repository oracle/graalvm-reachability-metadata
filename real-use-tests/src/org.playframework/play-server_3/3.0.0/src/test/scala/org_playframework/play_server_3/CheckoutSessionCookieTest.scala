/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_server_3

import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import play.api.Mode
import play.api.mvc.Results.Ok
import play.api.mvc.Results.Redirect
import play.core.server.Server
import play.core.server.ServerConfig

/** Exercises signed session-cookie transport across checkout redirects and session clearing. */
class CheckoutSessionCookieTest {
  @Test
  @Timeout(60)
  def remembersDeliveryPreferencesAndClearsThemWithoutAcceptingTamperedCookies(): Unit = {
    val config: ServerConfig = ServerConfig(
      port = Some(0),
      address = "127.0.0.1",
      mode = Mode.Test
    )

    Server.withRouterFromComponents(config) { components =>
      {
        case request if request.method == "POST" && request.path == "/checkout/warehouse" =>
          components.defaultActionBuilder(components.playBodyParsers.formUrlEncoded) { incoming =>
            val warehouse: String = incoming.body("warehouse").head
            Redirect("/checkout").withSession("warehouse" -> warehouse)
          }
        case request if request.method == "GET" && request.path == "/checkout" =>
          components.defaultActionBuilder { incoming =>
            Ok(s"Delivery warehouse: ${incoming.session.get("warehouse").getOrElse("not selected")}")
          }
        case request if request.method == "POST" && request.path == "/checkout/reset" =>
          components.defaultActionBuilder { _ =>
            Redirect("/checkout").withNewSession
          }
      }
    } { port =>
      val cookies: CookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL)
      val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .cookieHandler(cookies)
        .build()
      val independentClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
      try {
        val base: URI = URI.create(s"http://127.0.0.1:${port.value}")
        def checkout(): HttpResponse[String] = {
          val request: HttpRequest = HttpRequest.newBuilder(base.resolve("/checkout"))
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        val initial: HttpResponse[String] = checkout()
        assertEquals(200, initial.statusCode())
        assertEquals("Delivery warehouse: not selected", initial.body())

        val selection: HttpRequest = HttpRequest.newBuilder(base.resolve("/checkout/warehouse"))
          .timeout(Duration.ofSeconds(20))
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString("warehouse=north+depot"))
          .build()
        val selected: HttpResponse[String] = client.send(selection, HttpResponse.BodyHandlers.ofString())
        assertEquals(303, selected.statusCode())
        assertEquals("/checkout", selected.headers().firstValue("Location").orElseThrow())
        val sessionCookie: String = selected.headers().firstValue("Set-Cookie").orElseThrow()
        assertTrue(HttpCookie.parse(sessionCookie).get(0).isHttpOnly())

        val remembered: HttpResponse[String] = checkout()
        assertEquals(200, remembered.statusCode())
        assertEquals("Delivery warehouse: north depot", remembered.body())

        // A browser cannot change the signed cookie to invent another checkout session.
        val tampered: HttpRequest = HttpRequest.newBuilder(base.resolve("/checkout"))
          .timeout(Duration.ofSeconds(20))
          .header("Cookie", sessionCookie.takeWhile(_ != ';') + "x")
          .GET()
          .build()
        val rejected: HttpResponse[String] = independentClient.send(tampered, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, rejected.statusCode())
        assertEquals("Delivery warehouse: not selected", rejected.body())

        val reset: HttpRequest = HttpRequest.newBuilder(base.resolve("/checkout/reset"))
          .timeout(Duration.ofSeconds(20))
          .POST(HttpRequest.BodyPublishers.noBody())
          .build()
        val cleared: HttpResponse[String] = client.send(reset, HttpResponse.BodyHandlers.ofString())
        assertEquals(303, cleared.statusCode())
        assertEquals("/checkout", cleared.headers().firstValue("Location").orElseThrow())
        assertEquals(0L, HttpCookie.parse(cleared.headers().firstValue("Set-Cookie").orElseThrow())
          .get(0).getMaxAge())
        assertEquals("Delivery warehouse: not selected", checkout().body())
      } finally {
        independentClient.close()
        client.close()
      }
    }
  }
}

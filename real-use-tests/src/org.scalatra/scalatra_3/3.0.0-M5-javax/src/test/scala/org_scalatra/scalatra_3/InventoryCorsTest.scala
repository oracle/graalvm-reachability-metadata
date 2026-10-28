/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatra.scalatra_3

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import org.eclipse.jetty.http.HttpTester
import org.eclipse.jetty.server.{LocalConnector, Server}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.junit.jupiter.api.Assertions.{assertEquals, assertNull, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatra.{CorsSupport, ScalatraServlet}

/** Exercises CORS preflight and origin-specific response headers for a browser inventory editor. */
class InventoryCorsTest {
  @Test
  @Timeout(60)
  def permitsTheInventoryDashboardWithoutGrantingOtherOriginsAccess(): Unit = {
    val server: Server = new Server()
    val connector: LocalConnector = new LocalConnector(server)
    connector.setIdleTimeout(10000)
    server.addConnector(connector)
    server.setStopTimeout(10000)

    val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    context.setContextPath("/")
    context.setInitParameter(CorsSupport.AllowedOriginsKey, "https://inventory.example")
    context.setInitParameter(CorsSupport.AllowedMethodsKey, "GET,PUT")
    context.setInitParameter(CorsSupport.AllowedHeadersKey, "X-Warehouse")
    context.setInitParameter(CorsSupport.AllowCredentialsKey, "true")
    context.setInitParameter(CorsSupport.PreflightMaxAgeKey, "600")
    context.addServlet(new ServletHolder(new InventoryServlet()), "/*")
    server.setHandler(context)

    try {
      server.start()

      val preflight: HttpTester.Response = exchange(
        connector,
        "OPTIONS",
        "https://inventory.example",
        "Access-Control-Request-Method: PUT\r\nAccess-Control-Request-Headers: X-Warehouse\r\n"
      )
      assertEquals(200, preflight.getStatus)
      assertEquals("https://inventory.example", preflight.get("Access-Control-Allow-Origin"))
      assertEquals("true", preflight.get("Access-Control-Allow-Credentials"))
      assertEquals("600", preflight.get("Access-Control-Max-Age"))
      assertTrue(preflight.get("Access-Control-Allow-Methods").split(",").contains("PUT"))
      assertTrue(preflight.get("Access-Control-Allow-Headers").split(",").contains("X-Warehouse"))
      assertEquals("", preflight.getContent)

      val updated: HttpTester.Response = exchange(
        connector,
        "PUT",
        "https://inventory.example",
        "X-Warehouse: north\r\nContent-Type: text/plain\r\n",
        "12"
      )
      assertEquals(200, updated.getStatus)
      assertEquals("north stock updated to 12", updated.getContent)
      assertEquals("https://inventory.example", updated.get("Access-Control-Allow-Origin"))
      assertEquals("true", updated.get("Access-Control-Allow-Credentials"))

      val dashboard: HttpTester.Response = exchange(connector, "GET", "https://inventory.example")
      assertEquals(200, dashboard.getStatus)
      assertEquals("north stock: 12", dashboard.getContent)
      assertEquals("https://inventory.example", dashboard.get("Access-Control-Allow-Origin"))

      val otherOrigin: HttpTester.Response = exchange(connector, "GET", "https://untrusted.example")
      assertEquals(200, otherOrigin.getStatus)
      assertNull(otherOrigin.get("Access-Control-Allow-Origin"))
    } finally {
      try server.stop()
      finally server.destroy()
    }
  }

  private def exchange(
      connector: LocalConnector,
      method: String,
      origin: String,
      headers: String = "",
      body: String = ""
  ): HttpTester.Response = {
    val length: Int = body.getBytes(StandardCharsets.UTF_8).length
    val request: String =
      s"$method /inventory/north HTTP/1.1\r\nHost: localhost\r\nOrigin: $origin\r\n" +
        s"${headers}Content-Length: $length\r\nConnection: close\r\n\r\n$body"
    HttpTester.parseResponse(connector.getResponse(request, 10, TimeUnit.SECONDS))
  }

  private class InventoryServlet extends ScalatraServlet with CorsSupport {
    private var northStock: Int = 5

    get("/inventory/north") {
      contentType = "text/plain"
      s"north stock: $northStock"
    }

    put("/inventory/north") {
      contentType = "text/plain"
      if (request.getHeader("X-Warehouse") != "north") {
        halt(400, "Warehouse header required")
      }
      northStock = request.body.toInt
      s"north stock updated to $northStock"
    }
  }
}

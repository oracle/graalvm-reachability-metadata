/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatra.scalatra_3

import java.util.concurrent.TimeUnit

import org.eclipse.jetty.http.HttpTester
import org.eclipse.jetty.server.{LocalConnector, Server}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatra.ScalatraServlet

/** Exercises before and after request filters for an API-token-protected report endpoint. */
class ApiAccessFiltersTest {
  @Test
  @Timeout(60)
  def protectsReportsAndAddsSecurityHeaders(): Unit = {
    val server: Server = new Server()
    val connector: LocalConnector = new LocalConnector(server)
    connector.setIdleTimeout(10000)
    server.addConnector(connector)
    server.setStopTimeout(10000)

    val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    context.setContextPath("/")
    context.addServlet(new ServletHolder(new ReportServlet()), "/*")
    server.setHandler(context)

    try {
      server.start()

      val anonymous: HttpTester.Response = fetchReport(connector)
      assertEquals(401, anonymous.getStatus)
      assertEquals("Report API token required", anonymous.getContent)
      assertEquals("no-store", anonymous.get("Cache-Control"))

      val authorized: HttpTester.Response = fetchReport(connector, "Bearer report-reader")
      assertEquals(200, authorized.getStatus)
      assertEquals("Daily sales: 42 orders", authorized.getContent)
      assertEquals("no-store", authorized.get("Cache-Control"))
      assertEquals("nosniff", authorized.get("X-Content-Type-Options"))

      val invalidToken: HttpTester.Response = fetchReport(connector, "Bearer wrong-token")
      assertEquals(401, invalidToken.getStatus)
      assertEquals("Report API token required", invalidToken.getContent)

      val publicHealth: HttpTester.Response = exchange(connector, "/health", "")
      assertEquals(200, publicHealth.getStatus)
      assertEquals("ready", publicHealth.getContent)
    } finally {
      try server.stop()
      finally server.destroy()
    }
  }

  private def fetchReport(connector: LocalConnector, token: String = ""): HttpTester.Response =
    exchange(connector, "/reports/daily", token)

  private def exchange(connector: LocalConnector, path: String, token: String): HttpTester.Response = {
    val authorization: String = if (token.isEmpty) "" else s"Authorization: $token\r\n"
    val request: String =
      s"GET $path HTTP/1.1\r\nHost: localhost\r\n${authorization}Connection: close\r\n\r\n"
    HttpTester.parseResponse(connector.getResponse(request, 10, TimeUnit.SECONDS))
  }

  private class ReportServlet extends ScalatraServlet {
    before("/reports/*") {
      contentType = "text/plain"
      response.setHeader("Cache-Control", "no-store")
      if (request.getHeader("Authorization") != "Bearer report-reader") {
        halt(401, "Report API token required")
      }
    }

    after("/reports/*") {
      response.setHeader("X-Content-Type-Options", "nosniff")
    }

    get("/reports/daily") {
      "Daily sales: 42 orders"
    }

    get("/health") {
      contentType = "text/plain"
      "ready"
    }
  }
}

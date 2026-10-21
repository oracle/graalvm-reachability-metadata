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
import org.scalatra.{ApiFormats, ScalatraServlet}

/** Exercises API content negotiation for JSON clients and CSV inventory report downloads. */
class InventoryReportNegotiationTest {
  @Test
  @Timeout(60)
  def servesThePreferredReportFormatAndHonorsExplicitDownloads(): Unit = {
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

      val defaultReport: HttpTester.Response = fetchReport(connector, "", "")
      assertReport(defaultReport, "application/json", """{"sku":"hammer","available":12}""")

      val spreadsheet: HttpTester.Response =
        fetchReport(connector, "", "application/json;q=0.3, text/csv;q=0.9")
      assertReport(spreadsheet, "text/csv", "sku,available\nhammer,12\n")

      val apiClient: HttpTester.Response =
        fetchReport(connector, "", "text/csv;q=0.2, application/json;q=0.8")
      assertReport(apiClient, "application/json", """{"sku":"hammer","available":12}""")

      val explicitDownload: HttpTester.Response =
        fetchReport(connector, "?format=csv", "application/json")
      assertReport(explicitDownload, "text/csv", "sku,available\nhammer,12\n")

      val subsequentClient: HttpTester.Response = fetchReport(connector, "", "application/json")
      assertReport(subsequentClient, "application/json", """{"sku":"hammer","available":12}""")
    } finally {
      try server.stop()
      finally server.destroy()
    }
  }

  private def fetchReport(connector: LocalConnector, query: String, accept: String): HttpTester.Response = {
    val acceptHeader: String = if (accept.isEmpty) "" else s"Accept: $accept\r\n"
    val request: String =
      s"GET /inventory/report$query HTTP/1.1\r\nHost: localhost\r\n${acceptHeader}Connection: close\r\n\r\n"
    HttpTester.parseResponse(connector.getResponse(request, 10, TimeUnit.SECONDS))
  }

  private def assertReport(response: HttpTester.Response, mediaType: String, body: String): Unit = {
    assertEquals(200, response.getStatus)
    assertEquals(mediaType, response.get("Content-Type").split(";")(0))
    assertEquals("Accept", response.get("Vary"))
    assertEquals(body, response.getContent)
  }

  private class ReportServlet extends ScalatraServlet with ApiFormats {
    override def defaultFormat: Symbol = Symbol("json")

    addMimeMapping("text/csv", "csv")

    get("/inventory/report") {
      response.setHeader("Vary", "Accept")
      format match {
        case "csv" => "sku,available\nhammer,12\n"
        case "json" => """{"sku":"hammer","available":12}"""
        case _ => halt(406, "Report available as JSON or CSV")
      }
    }
  }
}

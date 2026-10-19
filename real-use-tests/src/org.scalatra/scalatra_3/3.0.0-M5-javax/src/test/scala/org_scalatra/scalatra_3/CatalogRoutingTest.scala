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

/** Exercises HTTP routing and path/query parameter binding for a warehouse catalog. */
class CatalogRoutingTest {
  @Test
  @Timeout(60)
  def retrievesStockUsingCategorySkuAndOptionalWarehouse(): Unit = {
    val server: Server = new Server()
    val connector: LocalConnector = new LocalConnector(server)
    connector.setIdleTimeout(10000)
    server.addConnector(connector)
    server.setStopTimeout(10000)

    val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    context.setContextPath("/")
    context.addServlet(new ServletHolder(new CatalogServlet()), "/*")
    server.setHandler(context)

    try {
      server.start()

      val defaultWarehouse: HttpTester.Response = requestStock(connector, "/catalog/tools/hammer")
      assertEquals(200, defaultWarehouse.getStatus)
      assertEquals("tools/hammer at main: 12 available", defaultWarehouse.getContent)

      val selectedWarehouse: HttpTester.Response =
        requestStock(connector, "/catalog/tools/hammer?warehouse=north+depot")
      assertEquals(200, selectedWarehouse.getStatus)
      assertEquals("tools/hammer at north depot: 4 available", selectedWarehouse.getContent)

      val otherItem: HttpTester.Response = requestStock(connector, "/catalog/garden/spade")
      assertEquals(200, otherItem.getStatus)
      assertEquals("garden/spade at main: 7 available", otherItem.getContent)
    } finally {
      try server.stop()
      finally server.destroy()
    }
  }

  private def requestStock(connector: LocalConnector, path: String): HttpTester.Response = {
    val request: String = s"GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
    HttpTester.parseResponse(connector.getResponse(request, 10, TimeUnit.SECONDS))
  }

  private class CatalogServlet extends ScalatraServlet {
    private val stock: Map[(String, String, String), Int] = Map(
      ("tools", "hammer", "main") -> 12,
      ("tools", "hammer", "north depot") -> 4,
      ("garden", "spade", "main") -> 7
    )

    get("/catalog/:category/:sku") {
      contentType = "text/plain"
      val category: String = params("category")
      val sku: String = params("sku")
      val warehouse: String = params.getOrElse("warehouse", "main")
      val available: Int = stock.getOrElse((category, sku, warehouse), 0)
      s"$category/$sku at $warehouse: $available available"
    }
  }
}

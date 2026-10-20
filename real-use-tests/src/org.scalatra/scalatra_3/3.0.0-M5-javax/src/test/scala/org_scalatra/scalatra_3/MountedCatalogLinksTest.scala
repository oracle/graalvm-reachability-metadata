/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatra.scalatra_3

import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit

import org.eclipse.jetty.http.HttpTester
import org.eclipse.jetty.server.{LocalConnector, Server}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.junit.jupiter.api.Assertions.{assertEquals, assertNull}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatra.{Route, ScalatraServlet, UrlGeneratorSupport}

/** Exercises reverse URL generation for catalog links under a mounted application and servlet. */
class MountedCatalogLinksTest {
  @Test
  @Timeout(60)
  def followsGeneratedItemLinksWithEncodedWarehouseSelections(): Unit = {
    val server: Server = new Server()
    val connector: LocalConnector = new LocalConnector(server)
    connector.setIdleTimeout(10000)
    server.addConnector(connector)
    server.setStopTimeout(10000)

    val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    context.setContextPath("/store")
    context.addServlet(new ServletHolder(new CatalogServlet()), "/catalog/*")
    server.setHandler(context)

    try {
      server.start()

      val defaultLink: HttpTester.Response = fetch(connector, "/store/catalog/links/hammer")
      assertEquals(200, defaultLink.getStatus)
      assertEquals("/store/catalog/items/hammer", defaultLink.getContent)
      val defaultItem: HttpTester.Response = fetch(connector, defaultLink.getContent)
      assertEquals(200, defaultItem.getStatus)
      assertEquals("hammer at main", defaultItem.getContent)

      val selectedLink: HttpTester.Response = fetch(
        connector,
        "/store/catalog/links/spade?warehouse=north+depot"
      )
      assertEquals(200, selectedLink.getStatus)
      val selectedUri: URI = URI.create(selectedLink.getContent)
      assertEquals("/store/catalog/items/spade", selectedUri.getPath)
      assertEquals("warehouse=north depot", URLDecoder.decode(selectedUri.getRawQuery, UTF_8))
      assertNull(selectedUri.getFragment)
      val selectedItem: HttpTester.Response = fetch(connector, selectedUri.toASCIIString)
      assertEquals(200, selectedItem.getStatus)
      assertEquals("spade at north depot", selectedItem.getContent)

      val nextLink: HttpTester.Response = fetch(connector, "/store/catalog/links/rake")
      assertEquals(200, nextLink.getStatus)
      assertEquals("/store/catalog/items/rake", nextLink.getContent)
      val nextItem: HttpTester.Response = fetch(connector, nextLink.getContent)
      assertEquals(200, nextItem.getStatus)
      assertEquals("rake at main", nextItem.getContent)
    } finally {
      try server.stop()
      finally server.destroy()
    }
  }

  private def fetch(connector: LocalConnector, path: String): HttpTester.Response = {
    val request: String = s"GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
    HttpTester.parseResponse(connector.getResponse(request, 10, TimeUnit.SECONDS))
  }

  private class CatalogServlet extends ScalatraServlet with UrlGeneratorSupport {
    private val itemRoute: Route = get("/items/:sku") {
      contentType = "text/plain"
      val sku: String = params("sku")
      val warehouse: String = params.getOrElse("warehouse", "main")
      s"$sku at $warehouse"
    }

    get("/links/:sku") {
      contentType = "text/plain"
      val routeParams: Map[String, String] = Map("sku" -> params("sku")) ++
        params.get("warehouse").map(warehouse => "warehouse" -> warehouse)
      url(itemRoute, routeParams, Seq.empty)
    }
  }
}

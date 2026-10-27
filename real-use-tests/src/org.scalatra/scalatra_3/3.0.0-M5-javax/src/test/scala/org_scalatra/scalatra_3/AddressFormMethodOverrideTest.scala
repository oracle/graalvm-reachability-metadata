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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatra.{MethodOverride, ScalatraServlet}

/** Exercises HTTP method override for browser forms that edit and delete a saved shipping address. */
class AddressFormMethodOverrideTest {
  @Test
  @Timeout(60)
  def updatesAndDeletesAnAddressThroughPostForms(): Unit = {
    val server: Server = new Server()
    val connector: LocalConnector = new LocalConnector(server)
    connector.setIdleTimeout(10000)
    server.addConnector(connector)
    server.setStopTimeout(10000)

    val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    context.setContextPath("/")
    context.addServlet(new ServletHolder(new AddressServlet()), "/*")
    server.setHandler(context)

    try {
      server.start()

      val initial: HttpTester.Response = exchange(connector, "GET")
      assertEquals(200, initial.getStatus)
      assertEquals("Shipping address: 10 Oak Street", initial.getContent)

      val plainPost: HttpTester.Response = exchange(connector, "POST", "street=99+Wrong+Road")
      assertEquals(405, plainPost.getStatus)
      assertEquals("Choose the update or delete form", plainPost.getContent)

      val unchanged: HttpTester.Response = exchange(connector, "GET")
      assertEquals(200, unchanged.getStatus)
      assertEquals("Shipping address: 10 Oak Street", unchanged.getContent)

      val updated: HttpTester.Response =
        exchange(connector, "POST", "_method=PUT&street=42+Maple+Avenue")
      assertEquals(200, updated.getStatus)
      assertEquals("Saved shipping address: 42 Maple Avenue", updated.getContent)

      val saved: HttpTester.Response = exchange(connector, "GET")
      assertEquals(200, saved.getStatus)
      assertEquals("Shipping address: 42 Maple Avenue", saved.getContent)

      val deleted: HttpTester.Response = exchange(connector, "POST", "_method=DELETE")
      assertEquals(200, deleted.getStatus)
      assertEquals("Shipping address removed", deleted.getContent)

      val missing: HttpTester.Response = exchange(connector, "GET")
      assertEquals(404, missing.getStatus)
      assertEquals("No saved shipping address", missing.getContent)
    } finally {
      try server.stop()
      finally server.destroy()
    }
  }

  private def exchange(connector: LocalConnector, method: String, body: String = ""): HttpTester.Response = {
    val length: Int = body.getBytes(StandardCharsets.UTF_8).length
    val request: String =
      s"$method /addresses/home HTTP/1.1\r\nHost: localhost\r\n" +
        s"Content-Type: application/x-www-form-urlencoded\r\nContent-Length: $length\r\n" +
        s"Connection: close\r\n\r\n$body"
    HttpTester.parseResponse(connector.getResponse(request, 10, TimeUnit.SECONDS))
  }

  private class AddressServlet extends ScalatraServlet with MethodOverride {
    private var shippingAddress: Option[String] = Some("10 Oak Street")

    get("/addresses/home") {
      contentType = "text/plain"
      val street: String = shippingAddress.getOrElse(halt(404, "No saved shipping address"))
      s"Shipping address: $street"
    }

    post("/addresses/home") {
      halt(405, "Choose the update or delete form")
    }

    put("/addresses/home") {
      contentType = "text/plain"
      val street: String = params("street")
      shippingAddress = Some(street)
      s"Saved shipping address: $street"
    }

    delete("/addresses/home") {
      contentType = "text/plain"
      shippingAddress = None
      "Shipping address removed"
    }
  }
}

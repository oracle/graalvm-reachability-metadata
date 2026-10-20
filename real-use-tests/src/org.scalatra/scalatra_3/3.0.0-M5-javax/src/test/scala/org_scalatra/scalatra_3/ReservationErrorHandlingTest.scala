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
import org.junit.jupiter.api.Assertions.{assertEquals, assertNull}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatra.ScalatraServlet

/** Exercises custom error and not-found handlers for an inventory reservation API. */
class ReservationErrorHandlingTest {
  @Test
  @Timeout(60)
  def translatesReservationFailuresIntoClientResponses(): Unit = {
    val server: Server = new Server()
    val connector: LocalConnector = new LocalConnector(server)
    connector.setIdleTimeout(10000)
    server.addConnector(connector)
    server.setStopTimeout(10000)

    val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    context.setContextPath("/")
    context.addServlet(new ServletHolder(new ReservationServlet()), "/*")
    server.setHandler(context)

    try {
      server.start()

      val unavailable: HttpTester.Response = exchange(connector, "POST", "/reservations/spade")
      assertEquals(409, unavailable.getStatus)
      assertEquals("Cannot reserve spade: out of stock", unavailable.getContent)
      assertEquals("out-of-stock", unavailable.get("X-Reservation-Error"))

      val missingProduct: HttpTester.Response = exchange(connector, "POST", "/reservations/unknown")
      assertEquals(404, missingProduct.getStatus)
      assertEquals("Unknown product: unknown", missingProduct.getContent)
      assertEquals("unknown-product", missingProduct.get("X-Reservation-Error"))

      val missingEndpoint: HttpTester.Response = exchange(connector, "GET", "/retired-api")
      assertEquals(404, missingEndpoint.getStatus)
      assertEquals("No reservation endpoint at /retired-api", missingEndpoint.getContent)

      val accepted: HttpTester.Response = exchange(connector, "POST", "/reservations/hammer")
      assertEquals(201, accepted.getStatus)
      assertEquals("Reserved hammer", accepted.getContent)
      assertNull(accepted.get("X-Reservation-Error"))
    } finally {
      try server.stop()
      finally server.destroy()
    }
  }

  private def exchange(connector: LocalConnector, method: String, path: String): HttpTester.Response = {
    val request: String =
      s"$method $path HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
    HttpTester.parseResponse(connector.getResponse(request, 10, TimeUnit.SECONDS))
  }

  private class OutOfStock(val sku: String) extends RuntimeException(s"Out of stock: $sku")

  private class UnknownProduct(val sku: String) extends RuntimeException(s"Unknown product: $sku")

  private class ReservationServlet extends ScalatraServlet {
    private val stock: Map[String, Int] = Map("hammer" -> 3, "spade" -> 0)

    error {
      case unavailable: OutOfStock =>
        contentType = "text/plain"
        status = 409
        response.setHeader("X-Reservation-Error", "out-of-stock")
        s"Cannot reserve ${unavailable.sku}: out of stock"
      case missing: UnknownProduct =>
        contentType = "text/plain"
        status = 404
        response.setHeader("X-Reservation-Error", "unknown-product")
        s"Unknown product: ${missing.sku}"
    }

    notFound {
      contentType = "text/plain"
      status = 404
      s"No reservation endpoint at ${request.getRequestURI}"
    }

    post("/reservations/:sku") {
      contentType = "text/plain"
      val sku: String = params("sku")
      val available: Int = stock.getOrElse(sku, throw new UnknownProduct(sku))
      if (available == 0) {
        throw new OutOfStock(sku)
      }
      status = 201
      s"Reserved $sku"
    }
  }
}

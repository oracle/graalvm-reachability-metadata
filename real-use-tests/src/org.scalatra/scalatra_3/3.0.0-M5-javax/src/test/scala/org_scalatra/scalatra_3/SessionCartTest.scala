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
import org.junit.jupiter.api.Assertions.{assertEquals, assertNotNull}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatra.ScalatraServlet

/** Exercises session-backed shopping cart state across requests and customer sessions. */
class SessionCartTest {
  @Test
  @Timeout(60)
  def retainsCartForOneCustomerAndClearsItAfterCheckout(): Unit = {
    val server: Server = new Server()
    val connector: LocalConnector = new LocalConnector(server)
    connector.setIdleTimeout(10000)
    server.addConnector(connector)
    server.setStopTimeout(10000)

    val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS)
    context.setContextPath("/")
    context.addServlet(new ServletHolder(new CartServlet()), "/*")
    server.setHandler(context)

    try {
      server.start()

      val firstAddition: HttpTester.Response = exchange(connector, "POST", "/cart/add")
      assertEquals(200, firstAddition.getStatus)
      assertEquals("Cart quantity: 1", firstAddition.getContent)
      val setCookie: String = firstAddition.get("Set-Cookie")
      assertNotNull(setCookie)
      val customerCookie: String = setCookie.takeWhile(_ != ';')

      val secondAddition: HttpTester.Response = exchange(connector, "POST", "/cart/add", customerCookie)
      assertEquals(200, secondAddition.getStatus)
      assertEquals("Cart quantity: 2", secondAddition.getContent)

      val returningCustomer: HttpTester.Response = exchange(connector, "GET", "/cart", customerCookie)
      assertEquals(200, returningCustomer.getStatus)
      assertEquals("Cart quantity: 2", returningCustomer.getContent)

      val otherCustomer: HttpTester.Response = exchange(connector, "GET", "/cart")
      assertEquals(200, otherCustomer.getStatus)
      assertEquals("Cart quantity: 0", otherCustomer.getContent)

      val checkout: HttpTester.Response = exchange(connector, "POST", "/cart/checkout", customerCookie)
      assertEquals(200, checkout.getStatus)
      assertEquals("Ordered 2 items", checkout.getContent)

      val afterCheckout: HttpTester.Response = exchange(connector, "GET", "/cart", customerCookie)
      assertEquals(200, afterCheckout.getStatus)
      assertEquals("Cart quantity: 0", afterCheckout.getContent)
    } finally {
      try server.stop()
      finally server.destroy()
    }
  }

  private def exchange(
      connector: LocalConnector,
      method: String,
      path: String,
      cookie: String = ""
  ): HttpTester.Response = {
    val cookieHeader: String = if (cookie.isEmpty) "" else s"Cookie: $cookie\r\n"
    val request: String =
      s"$method $path HTTP/1.1\r\nHost: localhost\r\n${cookieHeader}Content-Length: 0\r\nConnection: close\r\n\r\n"
    HttpTester.parseResponse(connector.getResponse(request, 10, TimeUnit.SECONDS))
  }

  private class CartServlet extends ScalatraServlet {
    private def quantity: Int = session.getAs[Int]("cart-quantity").getOrElse(0)

    get("/cart") {
      contentType = "text/plain"
      s"Cart quantity: $quantity"
    }

    post("/cart/add") {
      contentType = "text/plain"
      val updatedQuantity: Int = quantity + 1
      session("cart-quantity") = updatedQuantity
      s"Cart quantity: $updatedQuantity"
    }

    post("/cart/checkout") {
      contentType = "text/plain"
      val orderedQuantity: Int = quantity
      session.invalidate()
      s"Ordered $orderedQuantity items"
    }
  }
}

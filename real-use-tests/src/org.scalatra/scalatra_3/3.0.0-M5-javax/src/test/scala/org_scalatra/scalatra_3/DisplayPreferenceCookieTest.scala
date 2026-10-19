/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatra.scalatra_3

import java.net.HttpCookie
import java.util.concurrent.TimeUnit

import org.eclipse.jetty.http.HttpTester
import org.eclipse.jetty.server.{LocalConnector, Server}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.junit.jupiter.api.Assertions.{assertEquals, assertNotNull, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatra.{CookieOptions, ScalatraServlet}

/** Exercises cookie-based display preferences for a storefront without server-side sessions. */
class DisplayPreferenceCookieTest {
  @Test
  @Timeout(60)
  def remembersAndClearsTheCustomersPreferredCatalogLayout(): Unit = {
    val server: Server = new Server()
    val connector: LocalConnector = new LocalConnector(server)
    connector.setIdleTimeout(10000)
    server.addConnector(connector)
    server.setStopTimeout(10000)

    val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    context.setContextPath("/")
    context.addServlet(new ServletHolder(new StorefrontServlet()), "/*")
    server.setHandler(context)

    try {
      server.start()

      val initial: HttpTester.Response = exchange(connector, "GET", "/shop/catalog")
      assertEquals(200, initial.getStatus)
      assertEquals("Catalog layout: grid", initial.getContent)

      val saved: HttpTester.Response = exchange(connector, "POST", "/shop/layout/compact")
      assertEquals(200, saved.getStatus)
      assertEquals("Saved layout: compact", saved.getContent)
      val setCookie: String = saved.get("Set-Cookie")
      assertNotNull(setCookie)
      val preference: HttpCookie = HttpCookie.parse(setCookie).get(0)
      assertEquals("catalog-layout", preference.getName)
      assertEquals("compact", preference.getValue)
      assertEquals("/shop", preference.getPath)
      assertTrue(preference.isHttpOnly)
      assertTrue(setCookie.contains("Expires="))
      val cookie: String = setCookie.takeWhile(_ != ';')

      val returningCustomer: HttpTester.Response = exchange(connector, "GET", "/shop/catalog", cookie)
      assertEquals(200, returningCustomer.getStatus)
      assertEquals("Catalog layout: compact", returningCustomer.getContent)

      val otherCustomer: HttpTester.Response = exchange(connector, "GET", "/shop/catalog")
      assertEquals(200, otherCustomer.getStatus)
      assertEquals("Catalog layout: grid", otherCustomer.getContent)

      val cleared: HttpTester.Response = exchange(connector, "DELETE", "/shop/layout", cookie)
      assertEquals(200, cleared.getStatus)
      assertEquals("Catalog layout: grid", cleared.getContent)
      val expiredHeader: String = cleared.get("Set-Cookie")
      assertNotNull(expiredHeader)
      val expired: HttpCookie = HttpCookie.parse(expiredHeader).get(0)
      assertEquals("catalog-layout", expired.getName)
      assertEquals("", expired.getValue)
      assertEquals("/shop", expired.getPath)
      assertEquals(0L, expired.getMaxAge)

      val afterClearing: HttpTester.Response = exchange(connector, "GET", "/shop/catalog")
      assertEquals(200, afterClearing.getStatus)
      assertEquals("Catalog layout: grid", afterClearing.getContent)
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
      s"$method $path HTTP/1.1\r\nHost: localhost\r\n${cookieHeader}" +
        "Content-Length: 0\r\nConnection: close\r\n\r\n"
    HttpTester.parseResponse(connector.getResponse(request, 10, TimeUnit.SECONDS))
  }

  private class StorefrontServlet extends ScalatraServlet {
    private val preferenceOptions: CookieOptions =
      CookieOptions(path = "/shop", maxAge = 3600, httpOnly = true)

    private def layout: String = cookies.get("catalog-layout").getOrElse("grid")

    get("/shop/catalog") {
      contentType = "text/plain"
      s"Catalog layout: $layout"
    }

    post("/shop/layout/:layout") {
      contentType = "text/plain"
      val selected: String = params("layout")
      if (!Set("grid", "compact").contains(selected)) {
        halt(400, "Unknown catalog layout")
      }
      cookies.set("catalog-layout", selected)(using preferenceOptions)
      s"Saved layout: $layout"
    }

    delete("/shop/layout") {
      contentType = "text/plain"
      cookies.delete("catalog-layout")(using preferenceOptions)
      s"Catalog layout: $layout"
    }
  }
}

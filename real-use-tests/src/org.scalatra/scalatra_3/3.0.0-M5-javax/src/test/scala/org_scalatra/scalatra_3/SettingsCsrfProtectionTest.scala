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
import org.junit.jupiter.api.Assertions.{assertEquals, assertNotNull, assertFalse}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatra.{CsrfTokenSupport, ScalatraServlet}

/** Exercises CSRF protection for form and AJAX updates to account notification settings. */
class SettingsCsrfProtectionTest {
  @Test
  @Timeout(60)
  def requiresTheSessionTokenBeforeChangingNotificationSettings(): Unit = {
    val server: Server = new Server()
    val connector: LocalConnector = new LocalConnector(server)
    connector.setIdleTimeout(10000)
    server.addConnector(connector)
    server.setStopTimeout(10000)

    val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS)
    context.setContextPath("/")
    context.addServlet(new ServletHolder(new SettingsServlet()), "/*")
    server.setHandler(context)

    try {
      server.start()

      val initial: HttpTester.Response = exchange(connector, "GET")
      assertEquals(200, initial.getStatus)
      assertEquals("Notifications: daily", initial.getContent)
      val token: String = initial.get("X-CSRF-TOKEN")
      assertNotNull(token)
      assertFalse(token.isEmpty)
      val setCookie: String = initial.get("Set-Cookie")
      assertNotNull(setCookie)
      val cookie: String = setCookie.takeWhile(_ != ';')

      val missingToken: HttpTester.Response =
        exchange(connector, "POST", cookie, "frequency=never")
      assertEquals(403, missingToken.getStatus)
      assertEquals("Request tampering detected!", missingToken.getContent)

      val wrongToken: HttpTester.Response =
        exchange(connector, "POST", cookie, "frequency=never&_csrf=invalid-token")
      assertEquals(403, wrongToken.getStatus)

      val unchanged: HttpTester.Response = exchange(connector, "GET", cookie)
      assertEquals(200, unchanged.getStatus)
      assertEquals("Notifications: daily", unchanged.getContent)
      assertEquals(token, unchanged.get("X-CSRF-TOKEN"))

      val formUpdate: HttpTester.Response =
        exchange(connector, "POST", cookie, s"frequency=weekly&_csrf=$token")
      assertEquals(200, formUpdate.getStatus)
      assertEquals("Notifications: weekly", formUpdate.getContent)

      val ajaxUpdate: HttpTester.Response =
        exchange(connector, "POST", cookie, "frequency=never", token)
      assertEquals(200, ajaxUpdate.getStatus)
      assertEquals("Notifications: never", ajaxUpdate.getContent)

      val saved: HttpTester.Response = exchange(connector, "GET", cookie)
      assertEquals(200, saved.getStatus)
      assertEquals("Notifications: never", saved.getContent)

      val otherAccount: HttpTester.Response = exchange(connector, "GET")
      assertEquals(200, otherAccount.getStatus)
      assertEquals("Notifications: daily", otherAccount.getContent)
    } finally {
      try server.stop()
      finally server.destroy()
    }
  }

  private def exchange(
      connector: LocalConnector,
      method: String,
      cookie: String = "",
      body: String = "",
      token: String = ""
  ): HttpTester.Response = {
    val cookieHeader: String = if (cookie.isEmpty) "" else s"Cookie: $cookie\r\n"
    val tokenHeader: String = if (token.isEmpty) "" else s"X-CSRF-TOKEN: $token\r\n"
    val length: Int = body.getBytes(StandardCharsets.UTF_8).length
    val request: String =
      s"$method /settings HTTP/1.1\r\nHost: localhost\r\n$cookieHeader$tokenHeader" +
        s"Content-Type: application/x-www-form-urlencoded\r\nContent-Length: $length\r\n" +
        s"Connection: close\r\n\r\n$body"
    HttpTester.parseResponse(connector.getResponse(request, 10, TimeUnit.SECONDS))
  }

  private class SettingsServlet extends ScalatraServlet with CsrfTokenSupport {
    override def csrfKey: String = "_csrf"

    private def frequency: String = session.getAs[String]("frequency").getOrElse("daily")

    get("/settings") {
      contentType = "text/plain"
      response.setHeader("X-CSRF-TOKEN", csrfToken)
      s"Notifications: $frequency"
    }

    post("/settings") {
      contentType = "text/plain"
      val selected: String = params("frequency")
      if (!Set("daily", "weekly", "never").contains(selected)) {
        halt(400, "Unknown notification frequency")
      }
      session("frequency") = selected
      s"Notifications: $frequency"
    }
  }
}

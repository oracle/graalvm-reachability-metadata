/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatra.scalatra_3

import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import org.eclipse.jetty.http.HttpTester
import org.eclipse.jetty.server.{LocalConnector, Server}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.junit.jupiter.api.Assertions.{assertEquals, assertNotNull}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatra.{FlashMapSupport, ScalatraServlet}

/** Exercises flash messages across a post/redirect/get profile update workflow. */
class ProfileFlashMessageTest {
  @Test
  @Timeout(60)
  def showsAnUpdateConfirmationOnlyOnTheNextPage(): Unit = {
    val server: Server = new Server()
    val connector: LocalConnector = new LocalConnector(server)
    connector.setIdleTimeout(10000)
    server.addConnector(connector)
    server.setStopTimeout(10000)

    val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS)
    context.setContextPath("/")
    context.addServlet(new ServletHolder(new ProfileServlet()), "/*")
    server.setHandler(context)

    try {
      server.start()

      val saved: HttpTester.Response = exchange(connector, "POST", "/profile", "", "name=Alex+River")
      assertEquals(302, saved.getStatus)
      val destination: URI = URI.create(saved.get("Location"))
      assertEquals("/profile", destination.getPath.takeWhile(_ != ';'))
      val setCookie: String = saved.get("Set-Cookie")
      assertNotNull(setCookie)
      val cookie: String = setCookie.takeWhile(_ != ';')

      val otherVisitor: HttpTester.Response = exchange(connector, "GET", "/profile")
      assertEquals(200, otherVisitor.getStatus)
      assertEquals("Profile: guest | No notification", otherVisitor.getContent)

      val redirected: HttpTester.Response = exchange(connector, "GET", destination.getRawPath, cookie)
      assertEquals(200, redirected.getStatus)
      assertEquals("Profile: Alex River | Saved profile for Alex River", redirected.getContent)

      val refreshed: HttpTester.Response = exchange(connector, "GET", "/profile", cookie)
      assertEquals(200, refreshed.getStatus)
      assertEquals("Profile: Alex River | No notification", refreshed.getContent)
    } finally {
      try server.stop()
      finally server.destroy()
    }
  }

  private def exchange(
      connector: LocalConnector,
      method: String,
      path: String,
      cookie: String = "",
      body: String = ""
  ): HttpTester.Response = {
    val cookieHeader: String = if (cookie.isEmpty) "" else s"Cookie: $cookie\r\n"
    val length: Int = body.getBytes(StandardCharsets.UTF_8).length
    val request: String =
      s"$method $path HTTP/1.1\r\nHost: localhost\r\n${cookieHeader}" +
        s"Content-Type: application/x-www-form-urlencoded\r\nContent-Length: $length\r\n" +
        s"Connection: close\r\n\r\n$body"
    HttpTester.parseResponse(connector.getResponse(request, 10, TimeUnit.SECONDS))
  }

  private class ProfileServlet extends ScalatraServlet with FlashMapSupport {
    post("/profile") {
      val name: String = params("name")
      session("profile-name") = name
      flash("notification") = s"Saved profile for $name"
      redirect("/profile")
    }

    get("/profile") {
      contentType = "text/plain"
      val name: String = session.getAs[String]("profile-name").getOrElse("guest")
      val notification: String = flash.get("notification").map(_.toString).getOrElse("No notification")
      s"Profile: $name | $notification"
    }
  }
}

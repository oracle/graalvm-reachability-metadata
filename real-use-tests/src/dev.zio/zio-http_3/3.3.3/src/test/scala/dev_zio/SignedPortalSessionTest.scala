/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio

import java.time.Duration

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Runtime, Unsafe, ZIO}
import zio.http.*

/** Exercises ZIO HTTP signed cookies for a customer portal session. */
class SignedPortalSessionTest {
  private val sessionName: String = "portal-session"
  private val signingSecret: String = "portal-test-signing-key-not-for-production"

  @Test
  @Timeout(60)
  def authenticatesSignedSessionsRejectsTamperingAndClearsOnLogout(): Unit = {
    val program: ZIO[Any, Throwable, Unit] = for {
      // After credential verification, the portal issues a browser session cookie.
      session <- responseCookie(startSession("customer-alice"))
      _ <- ZIO.succeed {
        assertEquals(sessionName, session.name)
        assertEquals(Some(Path.root), session.path)
        assertEquals(Some(Duration.ofMinutes(30)), session.maxAge)
        assertEquals(Some(Cookie.SameSite.Lax), session.sameSite)
        assertTrue(session.isSecure)
        assertTrue(session.isHttpOnly)
      }
      browserCookie <- ZIO.fromEither(session.toRequest.encodeValidate(true))
      authenticated = Request.get("https://portal.example/account")
        .addHeader("Cookie", s"theme=dark; $browserCookie")
      _ <- assertAccount(authenticated, Status.Ok, "Account: customer-alice")
      _ <- assertAccount(
        Request.get("https://portal.example/account"),
        Status.Unauthorized,
        "Sign in required"
      )
      tampered = authenticated.removeHeader("Cookie")
        .addHeader("Cookie", browserCookie.replace("customer-alice", "customer-admin"))
      _ <- assertAccount(tampered, Status.Unauthorized, "Sign in required")
      unsigned = Request.get("https://portal.example/account")
        .addCookie(Cookie.Request(sessionName, "customer-admin"))
      _ <- assertAccount(unsigned, Status.Unauthorized, "Sign in required")
      cleared <- responseCookie(endSession())
      _ <- ZIO.succeed {
        assertEquals(sessionName, cleared.name)
        assertEquals("", cleared.content)
        assertEquals(Some(Duration.ZERO), cleared.maxAge)
        assertEquals(session.path, cleared.path)
      }
      // A browser applies Max-Age=0 by omitting the cookie from later requests.
      _ <- assertAccount(
        Request.get("https://portal.example/account").addCookie(Cookie.Request("theme", "dark")),
        Status.Unauthorized,
        "Sign in required"
      )
    } yield ()

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(program).getOrThrowFiberFailure()
    }
  }

  private def startSession(customerId: String): Response =
    Response.text("Signed in").addCookie(
      Cookie.Response(
        sessionName,
        customerId,
        path = Some(Path.root),
        isSecure = true,
        isHttpOnly = true,
        maxAge = Some(Duration.ofMinutes(30)),
        sameSite = Some(Cookie.SameSite.Lax)
      ).sign(signingSecret)
    )

  private def account(request: Request): Response =
    request.cookie(sessionName).flatMap(_.toRequest.unSign(signingSecret)) match {
      case Some(session) => Response.text(s"Account: ${session.content}")
      case None => Response.text("Sign in required").status(Status.Unauthorized)
    }

  private def endSession(): Response =
    Response.text("Signed out").addCookie(Cookie.clear(sessionName).copy(path = Some(Path.root)))

  private def responseCookie(response: Response): ZIO[Any, Throwable, Cookie.Response] =
    for {
      header <- ZIO.fromOption(response.rawHeader("Set-Cookie"))
        .orElseFail(new IllegalStateException("Session response must set a cookie"))
      cookie <- ZIO.fromEither(Cookie.Response.decode(header, validate = true))
    } yield cookie

  private def assertAccount(
      request: Request,
      expectedStatus: Status,
      expectedBody: String
  ): ZIO[Any, Throwable, Unit] = {
    val response: Response = account(request)
    for {
      body <- response.body.asString
      _ <- ZIO.succeed {
        assertEquals(expectedStatus, response.status)
        assertEquals(expectedBody, body)
      }
    } yield ()
  }
}

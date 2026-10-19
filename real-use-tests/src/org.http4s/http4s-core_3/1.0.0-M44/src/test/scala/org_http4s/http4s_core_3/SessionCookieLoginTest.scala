/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.effect.IO
import org.http4s.Headers
import org.http4s.HttpDate
import org.http4s.Method
import org.http4s.Request
import org.http4s.RequestCookie
import org.http4s.Response
import org.http4s.ResponseCookie
import org.http4s.SameSite
import org.http4s.Status
import org.http4s.headers.Cookie
import org.http4s.headers.`Set-Cookie`
import org.http4s.syntax.literals.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci.*

/**
 * Manages browser sessions with the cookie model: `ResponseCookie`, `RequestCookie`, and the
 * `Set-Cookie` and `Cookie` headers.
 *
 * A web shop issues a hardened session cookie and a readable locale preference at login, reads
 * them back from the browser's `Cookie` header on later requests, and expires the session at
 * logout.
 */
class SessionCookieLoginTest {

  @Test
  def issuesAHardenedSessionCookieAtLogin(): Unit = {
    val login: Request[IO] = Request[IO](method = Method.POST, uri = uri"/login")
    val response: Response[IO] = logIn(login, "s-7f3a9c", "de-AT")

    assertEquals(Status.SeeOther, response.status)
    val issued: List[ResponseCookie] = response.cookies
    assertEquals(List(SessionCookieName, LocaleCookieName), issued.map(_.name))

    val session: ResponseCookie = issued.head
    assertEquals("s-7f3a9c", session.content)
    assertTrue(session.secure)
    assertTrue(session.httpOnly)
    assertEquals(Some(SameSite.Strict), session.sameSite)
    assertEquals(Some(SessionLifetimeSeconds), session.maxAge)
    assertEquals(Some("/"), session.path)
    assertEquals(Some(SessionExpiry), session.expires)

    val rendered: List[String] = response.headers.get(ci"Set-Cookie").toList.flatMap(_.toList.map(_.value))
    assertEquals(2, rendered.size)
    val sessionLine: String = rendered.head
    assertTrue(sessionLine.startsWith("SID=s-7f3a9c"), sessionLine)
    assertTrue(sessionLine.contains("Expires=Wed, 21 Oct 2026 07:28:00 GMT"), sessionLine)
    assertTrue(sessionLine.contains("Max-Age=1800"), sessionLine)
    assertTrue(sessionLine.contains("Path=/"), sessionLine)
    assertTrue(sessionLine.contains("SameSite=Strict"), sessionLine)
    assertTrue(sessionLine.contains("Secure"), sessionLine)
    assertTrue(sessionLine.contains("HttpOnly"), sessionLine)
    assertEquals("locale=de-AT; Path=/; SameSite=Lax", rendered(1))
  }

  @Test
  def readsTheSessionBackFromTheBrowserCookieHeader(): Unit = {
    val browserRequest: Request[IO] = Request[IO](
      uri = uri"/account",
      headers = Headers("Cookie" -> "tracking=abc; SID=s-7f3a9c; locale=de-AT")
    )
    assertEquals(Some("s-7f3a9c"), sessionId(browserRequest))
    assertEquals("de-AT", locale(browserRequest))

    val cookieHeader: Cookie =
      browserRequest.headers.get[Cookie].getOrElse(throw new AssertionError("no Cookie header"))
    assertEquals(List("tracking", "SID", "locale"), cookieHeader.values.toList.map(_.name))

    val built: Request[IO] = Request[IO](uri = uri"/account")
      .addCookie(RequestCookie(SessionCookieName, "s-11aa"))
      .addCookie(LocaleCookieName, "fr-FR")
    assertEquals(Some("s-11aa"), sessionId(built))
    assertEquals("fr-FR", locale(built))
    assertEquals(
      Some("SID=s-11aa; locale=fr-FR"),
      built.headers.get(ci"Cookie").map(_.toList.map(_.value).mkString("; "))
    )

    val anonymous: Request[IO] = Request[IO](uri = uri"/account")
    assertEquals(None, sessionId(anonymous))
    assertEquals(DefaultLocale, locale(anonymous))
  }

  @Test
  def expiresTheSessionAtLogout(): Unit = {
    val response: Response[IO] = Response[IO](Status.SeeOther).removeCookie(SessionCookieName)
    val cleared: ResponseCookie = response.cookies.find(_.name == SessionCookieName)
      .getOrElse(throw new AssertionError("no cleared session cookie"))

    assertEquals("", cleared.content)
    assertTrue(cleared.expires.exists(expiry => expiry.epochSecond <= HttpDate.Epoch.epochSecond))

    val issued: ResponseCookie = sessionCookie("s-7f3a9c")
    val clearedIssued: ResponseCookie = issued.clearCookie
    assertEquals(SessionCookieName, clearedIssued.name)
    assertEquals("", clearedIssued.content)
    assertTrue(clearedIssued.expires.exists(_.epochSecond < SessionExpiry.epochSecond))
    assertEquals(issued.path, clearedIssued.path)
  }

  @Test
  def parsesSetCookieHeadersReceivedFromAnUpstreamService(): Unit = {
    val parsed: `Set-Cookie` = `Set-Cookie`
      .parse("cart=c-42; Domain=shop.example; Path=/cart; Max-Age=600; Secure; SameSite=None")
      .fold(failure => throw failure, identity)
    val cart: ResponseCookie = parsed.cookie
    assertEquals("cart", cart.name)
    assertEquals("c-42", cart.content)
    assertEquals(Some("shop.example"), cart.domain)
    assertEquals(Some("/cart"), cart.path)
    assertEquals(Some(600L), cart.maxAge)
    assertTrue(cart.secure)
    assertFalse(cart.httpOnly)
    assertEquals(Some(SameSite.None), cart.sameSite)

    val upstream: Response[IO] = Response[IO](
      headers = Headers("Set-Cookie" -> "a=1; Path=/", "Set-Cookie" -> "b=2; HttpOnly")
    )
    assertEquals(List("a" -> "1", "b" -> "2"), upstream.cookies.map(c => c.name -> c.content))
    assertTrue(upstream.cookies(1).httpOnly)

    assertTrue(Cookie.parse("SID=s-1; locale=en").isRight)
    assertTrue(HttpDate.fromString("not a date").isLeft)
  }

  /** The name of the cookie that carries the session identifier. */
  val SessionCookieName: String = "SID"

  /** The name of the cookie that carries the user's display locale. */
  val LocaleCookieName: String = "locale"

  /** The locale used when the browser sends no preference. */
  val DefaultLocale: String = "en-US"

  /** How long a session stays valid, in seconds. */
  val SessionLifetimeSeconds: Long = 1800L

  /** The fixed expiry the shop stamps on sessions issued in this scenario. */
  val SessionExpiry: HttpDate = HttpDate.unsafeFromString("Wed, 21 Oct 2026 07:28:00 GMT")

  /** Builds the hardened session cookie the shop issues at login. */
  def sessionCookie(id: String): ResponseCookie =
    ResponseCookie(
      name = SessionCookieName,
      content = id,
      expires = Some(SessionExpiry),
      maxAge = Some(SessionLifetimeSeconds),
      path = Some("/"),
      sameSite = Some(SameSite.Strict),
      secure = true,
      httpOnly = true
    )

  /** Redirects a successful login to the account page and sets the session and locale cookies. */
  def logIn(request: Request[IO], id: String, preferredLocale: String): Response[IO] = {
    assertEquals(Method.POST, request.method)
    Response[IO](Status.SeeOther)
      .addCookie(sessionCookie(id))
      .addCookie(
        ResponseCookie(
          name = LocaleCookieName,
          content = preferredLocale,
          path = Some("/"),
          sameSite = Some(SameSite.Lax)
        )
      )
  }

  /** Returns the session identifier the browser sent, if any. */
  def sessionId(request: Request[IO]): Option[String] =
    request.cookies.find(_.name == SessionCookieName).map(_.content)

  /** Returns the locale the browser asked for, or the default. */
  def locale(request: Request[IO]): String =
    request.cookies.find(_.name == LocaleCookieName).map(_.content).getOrElse(DefaultLocale)
}

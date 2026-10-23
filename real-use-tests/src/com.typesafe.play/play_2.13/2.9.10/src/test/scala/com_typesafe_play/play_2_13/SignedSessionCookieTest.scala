/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import play.api.http.{SecretConfiguration, SessionConfiguration}
import play.api.libs.crypto.DefaultCookieSigner
import play.api.mvc.{Cookie, DefaultCookieHeaderEncoding, DefaultSessionCookieBaker, Session}

/** Exercises Play signed session cookies for an account login, preference update, and logout. */
@Timeout(60)
class SignedSessionCookieTest {
  @Test
  def preservesAccountStateAndRejectsTamperedCookies(): Unit = {
    val secret: SecretConfiguration = SecretConfiguration(
      secret = "account-portal-test-secret-at-least-thirty-two-characters"
    )
    val configuration: SessionConfiguration = SessionConfiguration(
      cookieName = "ACCOUNT_SESSION",
      secure = true,
      httpOnly = true,
      path = "/account",
      sameSite = Some(Cookie.SameSite.Lax)
    )
    val sessions: DefaultSessionCookieBaker = new DefaultSessionCookieBaker(
      configuration, secret, new DefaultCookieSigner(secret)
    )
    val headers: DefaultCookieHeaderEncoding = new DefaultCookieHeaderEncoding()
    val signedIn: Session = Session(Map("userId" -> "customer-42", "theme" -> "light"))

    val loginCookie: Cookie = sessions.encodeAsCookie(signedIn)
    val setCookieHeader: String = headers.encodeSetCookieHeader(Seq(loginCookie))
    val browserCookie: Cookie = headers.fromSetCookieHeader(Some(setCookieHeader))(
      configuration.cookieName
    )

    assertEquals(configuration.cookieName, browserCookie.name)
    assertEquals("/account", browserCookie.path)
    assertTrue(browserCookie.secure)
    assertTrue(browserCookie.httpOnly)
    assertEquals(Some(Cookie.SameSite.Lax), browserCookie.sameSite)

    val requestHeader: String = headers.encodeCookieHeader(Seq(browserCookie))
    val received: Option[Cookie] = headers.fromCookieHeader(Some(requestHeader)).get(
      configuration.cookieName
    )
    val account: Session = sessions.decodeFromCookie(received)
    assertEquals(Some("customer-42"), account.get("userId"))
    assertEquals(Some("light"), account.get("theme"))

    val updated: Session = account + ("theme" -> "dark")
    val preferenceCookie: Cookie = sessions.encodeAsCookie(updated)
    assertEquals(updated, sessions.decodeFromCookie(Some(preferenceCookie)))
    assertEquals(Some("customer-42"), updated.get("userId"))

    val changeAt: Int = preferenceCookie.value.length / 2
    val replacement: Char = if (preferenceCookie.value.charAt(changeAt) == 'A') 'B' else 'A'
    val tampered: Cookie = preferenceCookie.copy(
      value = preferenceCookie.value.updated(changeAt, replacement)
    )
    assertTrue(sessions.decodeFromCookie(Some(tampered)).isEmpty)
    assertTrue(sessions.decodeFromCookie(None).isEmpty)

    val logoutCookie: Cookie = sessions.discard.toCookie
    assertEquals(loginCookie.name, logoutCookie.name)
    assertEquals(loginCookie.path, logoutCookie.path)
    assertTrue(logoutCookie.secure)
    assertTrue(logoutCookie.maxAge.exists(_ <= 0))
    assertEquals("", logoutCookie.value)
    assertTrue(sessions.decodeFromCookie(Some(logoutCookie)).isEmpty)
  }
}

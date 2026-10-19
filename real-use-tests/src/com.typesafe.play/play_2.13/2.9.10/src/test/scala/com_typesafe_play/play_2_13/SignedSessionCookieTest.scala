/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import play.api.http.{SecretConfiguration, SessionConfiguration}
import play.api.libs.crypto.DefaultCookieSigner
import play.api.mvc.{Cookie, DefaultCookieHeaderEncoding, DefaultSessionCookieBaker, Session}

/** Exercises Play's signed session cookies for a storefront login, preference update, and logout. */
@Timeout(60)
class SignedSessionCookieTest {
  @Test
  def preservesLoginAcrossRequestsAndRejectsTamperedCookies(): Unit = {
    val secret: SecretConfiguration = SecretConfiguration(
      secret = "storefront-test-secret-with-at-least-sixty-four-characters-for-signing"
    )
    val config: SessionConfiguration = SessionConfiguration(
      cookieName = "__Host-storefront",
      secure = true,
      httpOnly = true,
      path = "/",
      sameSite = Some(Cookie.SameSite.Lax)
    )
    val baker: DefaultSessionCookieBaker = new DefaultSessionCookieBaker(
      config, secret, new DefaultCookieSigner(secret)
    )
    val headers: DefaultCookieHeaderEncoding = new DefaultCookieHeaderEncoding()
    val login: Session = Session(Map("userId" -> "customer-42", "displayName" -> "Ada & Grace", "locale" -> "en"))
    val issued: Cookie = baker.encodeAsCookie(login)

    // The response carries the browser security policy as well as the signed session.
    val setCookie: String = headers.encodeSetCookieHeader(Seq(issued))
    val received: Cookie = headers.fromSetCookieHeader(Some(setCookie))(config.cookieName)
    assertEquals(config.cookieName, received.name)
    assertTrue(received.secure)
    assertTrue(received.httpOnly)
    assertEquals("/", received.path)
    assertEquals(Some(Cookie.SameSite.Lax), received.sameSite)
    assertEquals(None, received.maxAge)
    assertFalse(received.value.isEmpty)

    val requestHeader: String = headers.encodeCookieHeader(Seq(received))
    val requestCookie: Option[Cookie] = headers.fromCookieHeader(Some(requestHeader)).get(config.cookieName)
    val restored: Session = baker.decodeFromCookie(requestCookie)
    assertEquals(login, restored)
    assertEquals(Some("customer-42"), restored.get("userId"))
    assertEquals(Some("Ada & Grace"), restored.get("displayName"))

    val updated: Session = restored + ("locale" -> "fr")
    val updatedCookie: Cookie = baker.encodeAsCookie(updated)
    val nextRequest: String = headers.encodeCookieHeader(Seq(updatedCookie))
    val nextSession: Session = baker.decodeFromCookie(
      headers.fromCookieHeader(Some(nextRequest)).get(config.cookieName)
    )
    assertEquals(Some("fr"), nextSession.get("locale"))
    assertEquals(Some("customer-42"), nextSession.get("userId"))

    // A modified browser cookie must not authenticate the next request.
    val tampered: Cookie = updatedCookie.copy(value = updatedCookie.value + "tampered")
    assertTrue(baker.decodeFromCookie(Some(tampered)).isEmpty)
    assertTrue(baker.decodeFromCookie(None).isEmpty)

    val logout: Cookie = baker.discard.toCookie
    val logoutHeader: String = headers.encodeSetCookieHeader(Seq(logout))
    val discarded: Cookie = headers.fromSetCookieHeader(Some(logoutHeader))(config.cookieName)
    assertEquals(issued.name, discarded.name)
    assertEquals(issued.path, discarded.path)
    assertEquals(issued.domain, discarded.domain)
    assertTrue(discarded.secure)
    assertTrue(discarded.maxAge.exists(_ <= 0))
    assertTrue(baker.decodeFromCookie(Some(discarded)).isEmpty)
  }
}

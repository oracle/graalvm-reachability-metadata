/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_crypto_3

import org.http4s.server.middleware.CSRF
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@Timeout(30)
class SecureEqTest {
  @Test
  def comparesEqualTokensIncludingEmptyAndUtf8Values(): Unit = {
    assertTrue(CSRF.isEqual("session-token", new String("session-token".toCharArray)))
    assertTrue(CSRF.isEqual("", ""))
    assertTrue(CSRF.isEqual("caf\u00e9", "caf\u00e9"))
  }

  @Test
  def rejectsDifferentContentsAndLengthsInEitherOrder(): Unit = {
    assertFalse(CSRF.isEqual("session-token", "Session-token"))
    assertFalse(CSRF.isEqual("session-token", "session-tokeN"))
    assertFalse(CSRF.isEqual("token", "token-extra"))
    assertFalse(CSRF.isEqual("token-extra", "token"))
    assertFalse(CSRF.isEqual("", "token"))
    assertFalse(CSRF.isEqual("token", ""))
    assertFalse(CSRF.isEqual("caf\u00e9", "cafe"))
  }
}

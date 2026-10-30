/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_crypto_3

import cats.effect.SyncIO
import org.http4s.server.middleware.authentication.DigestAuth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@Timeout(30)
class HashTest {
  @Test
  def precomputesDigestAuthenticationCredentials(): Unit = {
    val digest: String = DigestAuth.Md5HashedAuthStore
      .precomputeHash[SyncIO]("Mufasa", "testrealm@host.com", "Circle Of Life")
      .unsafeRunSync()

    assertEquals("939e7578ed9e3c518a452acee763bce9", digest)

    val changedPassword: String = DigestAuth.Md5HashedAuthStore
      .precomputeHash[SyncIO]("Mufasa", "testrealm@host.com", "another password")
      .unsafeRunSync()
    assertNotEquals(digest, changedPassword)
  }
}

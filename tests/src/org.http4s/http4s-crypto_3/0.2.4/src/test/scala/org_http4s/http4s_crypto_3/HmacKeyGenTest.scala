/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_crypto_3

import cats.effect.SyncIO
import org.http4s.server.middleware.CSRF
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import javax.crypto.SecretKey

@Timeout(30)
class HmacKeyGenTest {
  @Test
  def generatesSigningKeysThatCanBePersistedAndReimported(): Unit = {
    val key: SecretKey = CSRF.generateSigningKey[SyncIO]().unsafeRunSync()
    assertEquals("HmacSHA1", key.getAlgorithm)
    assertEquals("RAW", key.getFormat)
    assertTrue(key.getEncoded.length >= 20)

    val signer: CSRF[SyncIO, SyncIO] = CSRF[SyncIO, SyncIO](key, _ => true).build
    val verifier: CSRF[SyncIO, SyncIO] = CSRF.withKeyBytes[SyncIO, SyncIO](key.getEncoded, _ => true)
      .unsafeRunSync().build
    val token: CSRF.CSRFToken = signer.generateToken[SyncIO].unsafeRunSync()
    val encoded: String = CSRF.unlift(token)
    val raw: String = encoded.takeWhile(_ != '-')

    assertEquals(64, raw.length)
    assertTrue(raw.forall(character => "0123456789ABCDEF".contains(character)))
    assertEquals(Right(raw), verifier.extractRaw(encoded))
  }
}

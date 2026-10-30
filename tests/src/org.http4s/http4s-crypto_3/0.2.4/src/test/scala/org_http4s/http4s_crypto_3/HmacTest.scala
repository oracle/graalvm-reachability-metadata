/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_crypto_3

import cats.effect.SyncIO
import org.http4s.Header
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.server.middleware.CSRF
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.ci.CIString

import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.crypto.spec.SecretKeySpec

@Timeout(30)
class HmacTest {
  private val keyBytes: Array[Byte] =
    "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII)
  private val clock: Clock = Clock.fixed(Instant.ofEpochMilli(1700000000000L), ZoneOffset.UTC)

  @Test
  def signsAndVerifiesTokensWithImportedJavaKeys(): Unit = {
    val vectors: List[(String, String)] = List(
      "HmacSHA1" -> "3810C359C88723C18CEA1B60B928D2F16AE29340",
      "HmacSHA256" -> "43593DDF76E969DC4C3E504AF536A159D795113015161DA3231474010269F451",
      "HmacSHA512" -> ("BF478F381DC62DFB65A85D705DC29EEBB93CCE02CFD4A854DEA6EA50AC3BD7A84" +
        "154844F4E7B94FF9DED9C3FD41902DA8707B778BD87B8E26B5285203B8AFA17"),
    )

    vectors.foreach { case (algorithm, signature) =>
      val csrf: CSRF[SyncIO, SyncIO] = CSRF[SyncIO, SyncIO](
        new SecretKeySpec(keyBytes, algorithm),
        _ => true,
      ).withClock(clock).build
      val token: CSRF.CSRFToken = csrf.signToken[SyncIO]("checkoutsession").unsafeRunSync()
      val encoded: String = CSRF.unlift(token)

      assertEquals(s"checkoutsession-1700000000000-$signature", encoded)
      assertEquals(Right("checkoutsession"), csrf.extractRaw(encoded))
      assertEquals(Left(CSRF.CSRFCheckFailed), csrf.extractRaw(encoded.replace("checkoutsession", "othersession")))
      assertEquals(Left(CSRF.CSRFCheckFailed), csrf.extractRaw(encoded.dropRight(2)))
    }
  }

  @Test
  def validatesDoubleSubmitTokensAndRejectsTamperedSignatures(): Unit = {
    val csrf: CSRF[SyncIO, SyncIO] = CSRF.withKeyBytes[SyncIO, SyncIO](keyBytes, _ => true)
      .unsafeRunSync().withClock(clock).build
    val token: CSRF.CSRFToken = csrf.signToken[SyncIO]("checkoutsession").unsafeRunSync()
    val request: Request[SyncIO] = csrf.embedInRequestCookie(Request[SyncIO](Method.POST), token)
      .putHeaders(Header.Raw(CIString("X-Csrf-Token"), CSRF.unlift(token)))
    val response: Response[SyncIO] = csrf.checkCSRF(request, SyncIO.pure(Response[SyncIO](Status.Accepted)))
      .unsafeRunSync()

    assertEquals(Status.Accepted, response.status)
    assertEquals("csrf-token", response.cookies.head.name)
    assertEquals(Right("checkoutsession"), csrf.extractRaw(response.cookies.head.content))

    val tampered: String = CSRF.unlift(token).replace("checkoutsession", "othersession")
    val rejected: Response[SyncIO] = csrf.checkCSRF(
      request.putHeaders(Header.Raw(CIString("X-Csrf-Token"), tampered)),
      SyncIO.raiseError(new AssertionError("Rejected requests must not execute the application")),
    ).unsafeRunSync()
    assertEquals(Status.Forbidden, rejected.status)

    val missing: Response[SyncIO] = csrf.checkCSRF(
      Request[SyncIO](Method.POST),
      SyncIO.raiseError(new AssertionError("Requests without tokens must not execute the application")),
    ).unsafeRunSync()
    assertEquals(Status.Forbidden, missing.status)
  }
}

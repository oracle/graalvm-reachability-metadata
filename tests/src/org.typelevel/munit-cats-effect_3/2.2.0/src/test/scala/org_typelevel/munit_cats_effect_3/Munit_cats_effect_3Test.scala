/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.munit_cats_effect_3

import cats.effect.SyncIO
import munit.CatsEffectAssertions.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Munit_cats_effect_3Test {
  @Test
  def syncIOAssertionsCanValidateMappedEffectValues(): Unit = {
    val response: SyncIO[(Int, String)] = SyncIO.pure((200, "created"))

    response
      .mapOrFail { case (200, body) => body }
      .assert(_.nonEmpty)
      .flatMap(_ => response.mapOrFail { case (status, _) => status })
      .assertEquals(200)
      .unsafeRunSync()
  }

  @Test
  def syncIOAssertionsCanInterceptExpectedFailures(): Unit = {
    val failure: SyncIO[Unit] = SyncIO.raiseError(new IllegalArgumentException("invalid payload"))

    val exception: IllegalArgumentException = failure
      .interceptMessage[IllegalArgumentException]("invalid payload")
      .unsafeRunSync()

    assertEquals("invalid payload", exception.getMessage)
  }
}

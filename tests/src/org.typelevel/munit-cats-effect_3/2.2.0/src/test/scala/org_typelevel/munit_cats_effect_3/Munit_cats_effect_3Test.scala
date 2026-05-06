/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.munit_cats_effect_3

import cats.effect.IO
import cats.effect.Resource
import cats.effect.SyncIO
import munit.CatsEffectAssertions.*
import munit.CatsEffectSuite
import munit.Location
import munit.TestOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class Munit_cats_effect_3Test {
  @Test
  def resourceFunFixtureAcquiresResourceAndRunsFinalizer(): Unit = {
    val suite: CatsEffectSuite = new CatsEffectSuite {}
    var acquisitions: Int = 0
    var releases: Int = 0
    val fixture: suite.FunFixture[String] = suite
      .ResourceFunFixture(
        Resource.make(IO {
          acquisitions += 1
          "managed-resource"
        })(_ => IO {
          releases += 1
        })
      )
      .unsafeRunSync()
    val testOptions: TestOptions = new TestOptions(
      "resource fun fixture lifecycle",
      Set.empty,
      Location.empty
    )
    val acquired: String = Await.result(fixture.setup(testOptions), 5.seconds)

    try {
      assertEquals("managed-resource", acquired)
      assertEquals(1, acquisitions)
      assertEquals(0, releases)
    } finally {
      Await.result(fixture.teardown(acquired), 5.seconds)
    }
    assertEquals(1, releases)
  }

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

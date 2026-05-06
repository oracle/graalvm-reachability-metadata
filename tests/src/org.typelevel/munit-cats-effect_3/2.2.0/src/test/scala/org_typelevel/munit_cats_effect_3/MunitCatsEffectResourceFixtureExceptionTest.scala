/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.munit_cats_effect_3

import cats.effect.IO
import cats.effect.Resource
import munit.catseffect.IOFixture
import munit.catseffect.ResourceFixture
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows

class MunitCatsEffectResourceFixtureExceptionTest {
  @org.junit.jupiter.api.Test
  def suiteLocalFixtureReportsAccessBeforeBeforeAll(): Unit = {
    val fixture: IOFixture[String] = ResourceFixture.suiteLocal(
      "suite-resource",
      Resource.pure[IO, String]("suite-value")
    )

    val exception: ResourceFixture.FixtureNotInstantiatedException = assertThrows(
      classOf[ResourceFixture.FixtureNotInstantiatedException],
      () => fixture()
    )

    assertTrue(exception.getMessage.contains("`suite-resource`"))
    assertTrue(exception.getMessage.contains("was not instantiated"))
  }

  @org.junit.jupiter.api.Test
  def testLocalFixtureReportsAccessBeforeBeforeEach(): Unit = {
    val fixture: IOFixture[Int] = ResourceFixture.testLocal(
      "test-resource",
      Resource.pure[IO, Int](42)
    )

    val exception: ResourceFixture.FixtureNotInstantiatedException = assertThrows(
      classOf[ResourceFixture.FixtureNotInstantiatedException],
      () => fixture()
    )

    assertTrue(exception.getMessage.contains("`test-resource`"))
    assertTrue(exception.getMessage.contains("was not instantiated"))
  }
}

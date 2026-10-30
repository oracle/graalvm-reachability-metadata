/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatest.events.SuiteCompleted

@Timeout(120)
class FrameworkInnerSkeletonObjectInputStream1Test {
  @Test
  def resolvesRemoteEventClassesThroughTheFrameworkClassLoader(): Unit = {
    val result = FrameworkFixtures.runRemote()
    assertEquals(Set("adds two values", "joins two words"), RunnerFixtures.succeeded(result.events))
    assertTrue(result.events.exists(_.isInstanceOf[SuiteCompleted]))
    assertEquals(Vector(sbt.testing.Status.Success, sbt.testing.Status.Success), result.statuses)
  }
}

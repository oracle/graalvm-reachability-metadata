/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatest.events.{RunCompleted, RunStarting}

@Timeout(120)
class RunnerTest {
  @Test
  def runsANamedSuiteAndSelectsAnIndividualTest(): Unit = {
    val events = RunnerFixtures.run(classOf[PassingSuite], "-t", "adds two values")
    assertEquals(Set("adds two values"), RunnerFixtures.succeeded(events))
    assertEquals(1, events.collectFirst { case e: RunStarting => e.testCount }.get)
    assertTrue(events.last.isInstanceOf[RunCompleted])
  }

  @Test
  def constructsTheAnnotatedWrapperForANamedScenario(): Unit = {
    val events = RunnerFixtures.run(classOf[WrappedScenario])
    assertEquals(Set("receives the wrapped scenario"), RunnerFixtures.succeeded(events))
  }
}

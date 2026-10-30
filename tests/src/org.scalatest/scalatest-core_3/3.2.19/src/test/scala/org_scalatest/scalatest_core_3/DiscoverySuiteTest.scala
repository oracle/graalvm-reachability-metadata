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
class DiscoverySuiteTest {
  @Test
  def runsBothPlainAndWrappedSuitesDiscoveredInAPackage(): Unit = {
    val events = RunnerFixtures.discover(Seq(classOf[PassingSuite], classOf[WrappedScenario]),
      "-m", "org_scalatest.scalatest_core_3")
    assertEquals(Set("adds two values", "joins two words", "receives the wrapped scenario"),
      RunnerFixtures.succeeded(events))
    val completed = events.collect { case event: SuiteCompleted => event.suiteName }
    assertTrue(completed.contains("PassingSuite"))
    assertTrue(completed.contains("ConfiguringSuite"))
  }
}

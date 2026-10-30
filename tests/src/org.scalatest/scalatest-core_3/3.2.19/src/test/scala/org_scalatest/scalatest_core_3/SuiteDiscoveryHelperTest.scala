/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatest.DoNotDiscover
import org.scalatest.events.{DiscoveryCompleted, DiscoveryStarting}

@DoNotDiscover
class HiddenSuite extends PassingSuite

@Timeout(120)
class SuiteDiscoveryHelperTest {
  @Test
  def discoversAccessibleAndWrappedSuitesButHonorsDoNotDiscover(): Unit = {
    val events = RunnerFixtures.discover(
      Seq(classOf[PassingSuite], classOf[WrappedScenario], classOf[HiddenSuite]),
      "-w", "org_scalatest.scalatest_core_3")
    assertTrue(events.exists(_.isInstanceOf[DiscoveryStarting]))
    assertTrue(events.exists(_.isInstanceOf[DiscoveryCompleted]))
    assertEquals(Set("adds two values", "joins two words", "receives the wrapped scenario"),
      RunnerFixtures.succeeded(events))
    assertEquals(3, events.count(_.isInstanceOf[org.scalatest.events.TestSucceeded]))
  }
}

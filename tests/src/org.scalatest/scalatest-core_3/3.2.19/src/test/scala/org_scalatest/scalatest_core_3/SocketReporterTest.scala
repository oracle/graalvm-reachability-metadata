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
class SocketReporterTest {
  @Test
  def sendsRunAndTestEventsToTheConfiguredSocket(): Unit = {
    val (passed, events) = SocketFixtures.receive(classOf[PassingSuite])
    assertTrue(passed)
    assertEquals(Set("adds two values", "joins two words"), RunnerFixtures.succeeded(events))
    assertEquals(2, events.head.asInstanceOf[RunStarting].testCount)
    assertTrue(events.last.isInstanceOf[RunCompleted])
  }
}

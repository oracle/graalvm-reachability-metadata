/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatest.events.{RunCompleted, RunStarting, TestSucceeded}

@Timeout(120)
class ReporterFactoryTest {
  @Test
  def loadsACustomReporterAndDeliversTheRunEvents(): Unit = {
    val events = RunnerFixtures.run(classOf[PassingSuite])
    assertEquals(2, events.count(_.isInstanceOf[TestSucceeded]))
    assertTrue(events.head.isInstanceOf[RunStarting])
    assertTrue(events.last.isInstanceOf[RunCompleted])
    assertEquals(2, events.head.asInstanceOf[RunStarting].testCount)
  }
}

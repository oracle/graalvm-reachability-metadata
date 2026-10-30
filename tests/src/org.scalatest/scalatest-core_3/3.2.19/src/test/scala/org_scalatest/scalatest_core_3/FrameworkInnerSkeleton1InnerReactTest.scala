/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatest.events.{RunCompleted, TestSucceeded}

@Timeout(120)
class FrameworkInnerSkeleton1InnerReactTest {
  @Test
  def aggregatesRemoteTestResultsIntoTheParentRunSummary(): Unit = {
    val result = FrameworkFixtures.runRemote()
    assertEquals(2, result.events.count(_.isInstanceOf[TestSucceeded]))
    val completed = result.events.collect { case event: RunCompleted => event }.last
    assertEquals(2, completed.summary.get.testsSucceededCount)
    assertEquals(1, completed.summary.get.suitesCompletedCount)
    assertTrue(result.summary.contains("All tests passed"))
  }
}

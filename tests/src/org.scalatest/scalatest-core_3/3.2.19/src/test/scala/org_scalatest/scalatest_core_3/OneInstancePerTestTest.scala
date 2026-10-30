/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatest.OneInstancePerTest
import org.scalatest.funsuite.AnyFunSuite

class IsolatedCounterSuite extends AnyFunSuite with OneInstancePerTest {
  var counter: Int = 0
  test("first isolated increment") {
    counter += 1
    assertEquals(1, counter)
  }
  test("second isolated increment") {
    counter += 1
    assertEquals(1, counter)
  }
}

@Timeout(120)
class OneInstancePerTestTest {
  @Test
  def createsAFreshSuiteForEveryTest(): Unit = {
    val suite: IsolatedCounterSuite = new IsolatedCounterSuite
    val reporter = RunnerFixtures.runSuite(suite)
    assertEquals(suite.testNames, RunnerFixtures.succeeded(reporter.events))
    assertEquals(0, suite.counter)
  }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatest.ParallelTestExecution
import org.scalatest.funsuite.AnyFunSuite

class ParallelCounterSuite extends AnyFunSuite with ParallelTestExecution {
  private var counter: Int = 0
  test("first parallel increment") {
    counter += 1
    assertEquals(1, counter)
  }
  test("second parallel increment") {
    counter += 1
    assertEquals(1, counter)
  }
}

@Timeout(120)
class ParallelTestExecutionTest {
  @Test
  def distributesTestsToSeparateSuiteInstances(): Unit = {
    val events = RunnerFixtures.run(classOf[ParallelCounterSuite], "-P2")
    assertEquals(Set("first parallel increment", "second parallel increment"),
      RunnerFixtures.succeeded(events))
  }
}

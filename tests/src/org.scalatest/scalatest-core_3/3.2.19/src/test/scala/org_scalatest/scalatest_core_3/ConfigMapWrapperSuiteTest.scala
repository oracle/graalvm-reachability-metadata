/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatest.{Args, ConfigMap, ConfigMapWrapperSuite, Filter}
import org.scalatest.funsuite.AnyFunSuite

class ConfiguredSuite(configuration: Map[String, Any]) extends AnyFunSuite {
  test("reads constructor configuration") {
    assertEquals("sample", configuration("dataset"))
  }
}

@Timeout(120)
class ConfigMapWrapperSuiteTest {
  @Test
  def inspectsAndRunsTheWrappedSuiteWithConstructorConfiguration(): Unit = {
    val suite: ConfigMapWrapperSuite = new ConfigMapWrapperSuite(classOf[ConfiguredSuite])
    assertEquals("ConfiguredSuite", suite.suiteName)
    assertEquals(Set("reads constructor configuration"), suite.testNames)
    assertEquals(1, suite.expectedTestCount(Filter()))
    val reporter: RecordingReporter = new RecordingReporter
    val status = suite.run(None, Args(reporter, configMap = ConfigMap("dataset" -> "sample")))
    assertTrue(status.isCompleted())
    assertTrue(status.succeeds())
    assertEquals(suite.testNames, RunnerFixtures.succeeded(reporter.events))
  }
}

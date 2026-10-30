/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatest.Suite
import org.scalatest.events.{TestIgnored, TopOfMethod}

class IgnoredMethodSuite extends Suite {
  override def testNames: Set[String] = Set("testDeferred")
  override def tags: Map[String, Set[String]] = Map("testDeferred" -> Set("org.scalatest.Ignore"))
  def testDeferred(): Unit = throw new AssertionError("An ignored test must not execute")
}

@Timeout(120)
class SuiteTest {
  @Test
  def reportsTheMethodLocationOfAnIgnoredTestWithoutRunningIt(): Unit = {
    val suite: IgnoredMethodSuite = new IgnoredMethodSuite
    val reporter = RunnerFixtures.runSuite(suite)
    val ignored = reporter.events.collect { case event: TestIgnored => event }
    assertEquals(1, ignored.size)
    assertEquals("testDeferred", ignored.head.testName)
    assertTrue(ignored.head.location.get.isInstanceOf[TopOfMethod])
    val location = ignored.head.location.get.asInstanceOf[TopOfMethod]
    assertEquals(classOf[IgnoredMethodSuite].getName, location.className)
    assertTrue(location.methodId.contains("testDeferred"))
  }
}

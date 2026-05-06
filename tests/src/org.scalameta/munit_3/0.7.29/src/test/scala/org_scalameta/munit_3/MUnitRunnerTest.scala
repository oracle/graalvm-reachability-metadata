/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.munit_3

import munit.FunSuite
import munit.MUnitRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.runner.Description
import org.junit.runner.notification.RunNotifier

class MUnitRunnerTest {
  @Test
  def constructsSuiteWithPublicNoArgConstructor(): Unit = {
    val propertyName: String = "org_scalameta.munit_3.MUnitRunnerTest.executed"
    System.clearProperty(propertyName)

    try {
      val runner: MUnitRunner = new MUnitRunner(classOf[MUnitRunnerFixtureSuite])
      val description: Description = runner.getDescription()

      assertEquals(classOf[MUnitRunnerFixtureSuite].getName, description.getClassName)
      assertEquals(1, description.getChildren.size())

      runner.run(new RunNotifier())

      assertEquals("true", System.getProperty(propertyName))
      assertNotNull(runner.getDescription())
    } finally {
      System.clearProperty(propertyName)
    }
  }
}

class MUnitRunnerFixtureSuite extends FunSuite {
  test("runs through MUnitRunner") {
    System.setProperty("org_scalameta.munit_3.MUnitRunnerTest.executed", "true")
  }
}

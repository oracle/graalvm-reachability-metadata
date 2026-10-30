/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import org.junit.jupiter.api.Assertions.{assertThrows, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatest.Assertions
import org.scalatest.exceptions.TestFailedException

@Timeout(120)
class ResourcesTest {
  @Test
  def formatsAnAssertionFailureUsingTheScalaTestMessageBundle(): Unit = {
    val failure = assertThrows(classOf[TestFailedException], () => {
      Assertions.assertResult(12)(7)
      ()
    })
    assertTrue(failure.getMessage.contains("12"))
    assertTrue(failure.getMessage.contains("7"))
    assertTrue(failure.getMessage.contains("Expected"))
  }
}

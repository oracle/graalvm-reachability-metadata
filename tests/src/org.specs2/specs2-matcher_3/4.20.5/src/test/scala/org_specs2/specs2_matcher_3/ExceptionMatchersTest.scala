/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_specs2.specs2_matcher_3

import org.junit.jupiter.api.Assertions.{assertFalse, assertTrue}
import org.junit.jupiter.api.Test
import org.specs2.matcher.{MatchResult, MustMatchers}

class ExceptionMatchersTest extends MustMatchers {
  @Test
  def checksExceptionTypesAndMessagesFromUnevaluatedExpressions(): Unit = {
    val matching: MatchResult[Any] = "invalid".toInt must throwA[NumberFormatException]("invalid")
    assertTrue(matching.isSuccess, matching.message)
    val missing: MatchResult[Any] = "42".toInt must throwA[NumberFormatException]
    assertTrue(missing.toResult.isFailure)
    assertTrue(missing.message.contains("Got nothing"), missing.message)
    val wrongType: MatchResult[Any] = "invalid".toInt must throwA[IllegalStateException]
    assertFalse(wrongType.isSuccess)
  }

  @Test
  def checksExceptionInstancesAndAdditionalProperties(): Unit = {
    assertTrue((require(false, "quantity must be positive") must
      throwA(new IllegalArgumentException("requirement failed: quantity must be positive"))).isSuccess)
    val checked: MatchResult[Any] = "invalid".toInt must throwA[NumberFormatException].like {
      case error: NumberFormatException => error.getMessage must contain("invalid")
    }
    assertTrue(checked.isSuccess, checked.message)
  }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_specs2.specs2_matcher_3

import scala.util.Try
import org.junit.jupiter.api.Assertions.{assertFalse, assertTrue}
import org.junit.jupiter.api.Test
import org.specs2.matcher.MustMatchers

class TryMatchersTest extends MustMatchers {
  @Test
  def checksSuccessfulComputationsAndTheirResults(): Unit = {
    val parsed: Try[Int] = Try("42".toInt)
    assertTrue((parsed must beSuccessfulTry(42)).isSuccess)
    assertTrue((parsed must beSuccessfulTry[Int].withValue(beGreaterThan(40))).isSuccess)
    assertFalse((parsed must beSuccessfulTry(43)).isSuccess)
    assertFalse((parsed must beFailedTry[Int]).isSuccess)
  }

  @Test
  def checksFailedComputationsByExceptionTypeAndMessage(): Unit = {
    val parsed: Try[Int] = Try("not-a-number".toInt)
    assertTrue((parsed must beFailedTry[Int]).isSuccess)
    assertTrue((parsed must beFailedTry[Int].withThrowable[NumberFormatException](".*not-a-number.*")).isSuccess)
    assertFalse((parsed must beFailedTry[Int].withThrowable[IllegalStateException]).isSuccess)
    assertFalse((parsed must beSuccessfulTry[Int]).isSuccess)
  }
}

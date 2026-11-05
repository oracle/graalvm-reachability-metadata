/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_specs2.specs2_matcher_3

import org.junit.jupiter.api.Assertions.{assertFalse, assertTrue}
import org.junit.jupiter.api.Test
import org.specs2.matcher.MustMatchers

class EitherMatchersTest extends MustMatchers {
  @Test
  def distinguishesLeftAndRightAndChecksTheirValues(): Unit = {
    val accepted: Either[String, Int] = Right(24)
    val rejected: Either[String, Int] = Left("invalid quantity")
    assertTrue((accepted must beRight(24)).isSuccess)
    assertTrue((rejected must beLeft("invalid quantity")).isSuccess)
    assertFalse((rejected must beRight[Int]).isSuccess)
    assertFalse((accepted must beLeft[String]).isSuccess)
    assertFalse((accepted must beRight(25)).isSuccess)
  }

  @Test
  def appliesNestedChecksToEitherValues(): Unit = {
    val accepted: Either[String, Int] = Right(24)
    val rejected: Either[String, Int] = Left("invalid quantity")
    assertTrue((accepted must beRight(beGreaterThan(20))).isSuccess)
    assertTrue((rejected must beLeft(startWith("invalid"))).isSuccess)
    assertFalse((rejected must beLeft(contain("price"))).isSuccess)
  }
}

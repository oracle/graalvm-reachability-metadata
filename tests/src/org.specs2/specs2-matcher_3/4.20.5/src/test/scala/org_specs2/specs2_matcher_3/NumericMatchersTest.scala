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

class NumericMatchersTest extends MustMatchers {
  @Test
  def distinguishesStrictAndInclusiveComparisons(): Unit = {
    assertTrue((6 must beLessThan(7)).isSuccess)
    assertFalse((7 must beLessThan(7)).isSuccess)
    assertTrue((7 must beLessThanOrEqualTo(7)).isSuccess)
    assertTrue((8 must beGreaterThan(7)).isSuccess)
    assertTrue((7 must beGreaterThanOrEqualTo(7)).isSuccess)
  }

  @Test
  def checksRangeBoundaries(): Unit = {
    assertTrue((3 must beBetween(3, 9)).isSuccess)
    assertTrue((9 must beBetween(3, 9)).isSuccess)
    assertTrue((6 must beBetween(3, 9).excludingBounds).isSuccess)
    assertFalse((3 must beBetween(3, 9).excludingStart).isSuccess)
    assertFalse((9 must beBetween(3, 9).excludingEnd).isSuccess)
  }

  @Test
  def comparesFloatingPointValuesWithTolerance(): Unit = {
    assertTrue((12.04 must beCloseTo(12.0, 0.1)).isSuccess)
    assertFalse((12.3 must beCloseTo(12.0, 0.1)).isSuccess)
    assertTrue((12.04 must beCloseTo(12.0, 3.significantFigures)).isSuccess)
  }
}

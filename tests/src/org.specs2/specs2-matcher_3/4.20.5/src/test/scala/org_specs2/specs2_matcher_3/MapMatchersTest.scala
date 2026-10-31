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

class MapMatchersTest extends MustMatchers {
  @Test
  def checksKeysValuesAndAssociations(): Unit = {
    val inventory: Map[String, Int] = Map("pencils" -> 12, "notebooks" -> 5)
    assertTrue((inventory must haveKeys("pencils", "notebooks")).isSuccess)
    assertTrue((inventory must haveValues(12, 5)).isSuccess)
    assertTrue((inventory must havePairs("pencils" -> 12, "notebooks" -> 5)).isSuccess)
    assertFalse((inventory must havePair("pencils" -> 5)).isSuccess)
    assertTrue((inventory must not(haveKey("erasers"))).isSuccess)
    assertTrue((inventory must not(haveValue(99))).isSuccess)
  }

  @Test
  def checksPartialFunctionDomainsAndResults(): Unit = {
    val labels: PartialFunction[Int, String] = {
      case 1 => "first"
      case 2 => "second"
    }
    assertTrue((labels must beDefinedAt(1, 2)).isSuccess)
    assertTrue((labels must beDefinedBy(1 -> "first", 2 -> "second")).isSuccess)
    assertFalse((labels must beDefinedAt(3)).isSuccess)
    assertFalse((labels must beDefinedBy(1 -> "second")).isSuccess)
  }
}

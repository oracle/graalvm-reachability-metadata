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

class OptionMatchersTest extends MustMatchers {
  @Test
  def checksPresenceAbsenceAndContainedValues(): Unit = {
    val present: Option[Int] = Some(8)
    val absent: Option[Int] = None
    assertTrue((present must beSome(8)).isSuccess)
    assertTrue((absent must beNone).isSuccess)
    assertFalse((absent must beSome[Int]).isSuccess)
    assertFalse((present must beSome(9)).isSuccess)
    assertTrue((present must beAsNoneAs(Some(99))).isSuccess)
    assertTrue((absent must beAsNoneAs(None)).isSuccess)
  }

  @Test
  def checksContainedValuesUsingMatchersAndPartialFunctions(): Unit = {
    assertTrue((Option(8) must beSome(beGreaterThan(5))).isSuccess)
    assertTrue((Option("ready") must beSome[String].like {
      case value if value.startsWith("r") => value must endWith("dy")
    }).isSuccess)
    assertFalse((Option("waiting") must beSome[String].like {
      case "ready" => true must beTrue
    }).isSuccess)
  }
}

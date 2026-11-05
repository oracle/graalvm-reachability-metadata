/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_specs2.specs2_matcher_3

import java.util.regex.Pattern
import org.junit.jupiter.api.Assertions.{assertFalse, assertTrue}
import org.junit.jupiter.api.Test
import org.specs2.matcher.{MatchResult, MustMatchers}

class StringMatchersTest extends MustMatchers {
  @Test
  def checksSubstringsAndBoundaries(): Unit = {
    val text: String = "order: shipped"
    assertTrue((text must startWith("order:")).isSuccess)
    assertTrue((text must endWith("shipped")).isSuccess)
    assertTrue((text must contain(": ")).isSuccess)
    val mismatch: MatchResult[String] = text must contain("pending")
    assertTrue(mismatch.toResult.isFailure)
    assertTrue(mismatch.message.contains("pending"), mismatch.message)
  }

  @Test
  def matchesRegularExpressionsAndCapturedGroups(): Unit = {
    assertTrue(("AB-123" must beMatching("[A-Z]{2}-[0-9]{3}")).isSuccess)
    assertTrue(("AB-123" must beMatching(Pattern.compile("ab-123", Pattern.CASE_INSENSITIVE))).isSuccess)
    assertTrue(("AB-123" must beMatching("[A-Z]+-[0-9]+".r)).isSuccess)
    assertTrue(("order 17 then 29" must find("([0-9]+)").withGroups("17", "29")).isSuccess)
    assertFalse(("AB-123 extra" must beMatching("[A-Z]{2}-[0-9]{3}")).isSuccess)
  }

  @Test
  def adaptsEqualityForCaseWhitespaceAndTrimming(): Unit = {
    assertTrue(("READY" must beEqualTo("ready").ignoreCase).isSuccess)
    assertTrue(("a b\tc" must beEqualTo("abc").ignoreSpace).isSuccess)
    assertTrue(("  ready  " must beEqualTo("ready").trimmed).isSuccess)
  }
}

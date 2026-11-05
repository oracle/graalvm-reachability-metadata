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

class AnyMatchersTest extends MustMatchers {
  @Test
  def comparesValuesAndReportsExpectedAndActualValues(): Unit = {
    assertTrue((List(2, 4) must beEqualTo(List(2, 4))).isSuccess)
    val mismatch: MatchResult[String] = "received" must beEqualTo("expected")
    assertTrue(mismatch.toResult.isFailure)
    assertTrue(mismatch.message.contains("received"), mismatch.message)
    assertTrue(mismatch.message.contains("expected"), mismatch.message)
  }

  @Test
  def distinguishesIdentityNullAndBooleanValues(): Unit = {
    val shared: StringBuilder = new StringBuilder("payload")
    assertTrue((shared must beTheSameAs(shared)).isSuccess)
    assertFalse((shared must beTheSameAs(new StringBuilder("payload"))).isSuccess)
    assertTrue(((null: String) must beNull[String]).isSuccess)
    assertTrue((true must beTrue).isSuccess)
    assertTrue((false must beFalse).isSuccess)
    assertTrue(("ready" must beOneOf("ready", "waiting")).isSuccess)
  }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_specs2.specs2_matcher_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.Test
import org.specs2.matcher.{Expectable, Matcher, MatchResult, MustMatchers}

class MatcherTest extends MustMatchers {
  @Test
  def composesMatchersWithConjunctionDisjunctionAndNegation(): Unit = {
    val range: Matcher[Int] = beGreaterThan(3).and(beLessThan(10))
    assertTrue((7 must range).isSuccess)
    assertFalse((12 must range).isSuccess)
    assertTrue((12 must range.or(beEqualTo(12))).isSuccess)
    assertTrue((2 must range.not).isSuccess)
    assertTrue(range.test(7))
    assertFalse(range.test(2))
  }

  @Test
  def adaptsMatchersToDomainObjectsAndCustomizesDiagnostics(): Unit = {
    val longName: Matcher[Customer] = beGreaterThan(3).^^((customer: Customer) => customer.name.length)
    assertTrue((Customer("Alice") must longName).isSuccess)
    val mismatch: MatchResult[Customer] = Customer("Al") must longName.setMessage("name is too short")
    assertTrue(mismatch.toResult.isFailure)
    assertEquals("name is too short", mismatch.message)
  }

  @Test
  def evaluatesAnExpectableOnceWhenApplyingMultipleChecks(): Unit = {
    var evaluations: Int = 0
    val expectable: Expectable[Int] = createExpectable {
      evaluations += 1
      7
    }
    assertEquals(0, evaluations)
    assertTrue(expectable.applyMatcher(beGreaterThan(3)).isSuccess)
    assertTrue(expectable.applyMatcher(beLessThan(10)).isSuccess)
    assertEquals(1, evaluations)
    assertEquals(14, expectable.map((value: Int) => value * 2).value)
  }
}

final case class Customer(name: String)

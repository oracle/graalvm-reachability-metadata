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

class TraversableMatchersTest extends MustMatchers {
  @Test
  def checksMembershipMultiplicityAndOrder(): Unit = {
    val values: List[Int] = List(2, 3, 2)
    assertTrue((values must contain(2).exactly(2)).isSuccess)
    assertTrue((values must containAllOf(Seq(2, 3))).isSuccess)
    assertTrue((values must contain(exactly(2, 3, 2).inOrder)).isSuccess)
    assertFalse((values must contain(exactly(2, 2, 3).inOrder)).isSuccess)
    assertTrue((values must contain(beGreaterThan(1)).foreach).isSuccess)
  }

  @Test
  def checksSizesSortingAndUnorderedContents(): Unit = {
    assertTrue((List(1, 2, 3) must haveSize[List[Int]](3)).isSuccess)
    assertTrue((List.empty[Int] must beEmpty[List[Int]]).isSuccess)
    assertTrue((List(1, 2, 3) must beSorted[Int]).isSuccess)
    assertFalse((List(3, 1, 2) must beSorted[Int]).isSuccess)
    assertTrue((List(3, 1, 2) must containTheSameElementsAs(Seq(1, 2, 3))).isSuccess)
    val mismatch: MatchResult[List[Int]] = List(1, 2) must containTheSameElementsAs(Seq(1, 3))
    assertTrue(mismatch.toResult.isFailure)
    assertTrue(mismatch.message.contains("missing"), mismatch.message)
    assertTrue(mismatch.message.contains("3"), mismatch.message)
  }
}

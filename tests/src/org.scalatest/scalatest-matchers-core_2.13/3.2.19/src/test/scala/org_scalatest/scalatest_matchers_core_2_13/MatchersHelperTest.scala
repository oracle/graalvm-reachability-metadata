/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_matchers_core_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.scalactic.{Prettifier, source}
import org.scalatest.matchers.MatchResult
import org.scalatest.matchers.dsl.MatcherWords

class MatchersHelperTest extends MatcherWords {

  @Test
  def booleanSymbolMatcherInvokesScalaStylePredicateMethod(): Unit = {
    val item: MethodBackedProperty = new MethodBackedProperty

    val result: MatchResult = symbolMatchResult(item, "ready")

    assertThat(result.matches).isTrue()
  }

  @Test
  def booleanSymbolMatcherInvokesJavaBeanStylePredicateMethod(): Unit = {
    val item: BeanMethodBackedProperty = new BeanMethodBackedProperty

    val result: MatchResult = symbolMatchResult(item, "ready")

    assertThat(result.matches).isTrue()
  }

  @Test
  def booleanSymbolMatcherReadsPublicBooleanField(): Unit = {
    val item: scala.runtime.BooleanRef = new scala.runtime.BooleanRef(true)

    val result: MatchResult = symbolMatchResult(item, "elem")

    assertThat(result.matches).isTrue()
  }

  private def symbolMatchResult(item: AnyRef, propertyName: String): MatchResult = {
    val position: source.Position = source.Position("MatchersHelperTest.scala", "MatchersHelperTest.scala", 1)

    be.apply(Symbol(propertyName))(Prettifier.default, position)(item)
  }
}

class MethodBackedProperty {
  def ready: Boolean = true
}

class BeanMethodBackedProperty {
  def isReady: Boolean = true
}

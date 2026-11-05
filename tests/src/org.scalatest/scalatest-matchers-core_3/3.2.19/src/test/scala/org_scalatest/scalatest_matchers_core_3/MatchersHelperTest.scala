/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_matchers_core_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.scalactic.{Prettifier, source}
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.matchers.dsl.MatcherWords
import scala.runtime.BooleanRef

class MatchersHelperTest extends MatcherWords {

  @Test
  def booleanSymbolMatcherReadsPublicField(): Unit = {
    val matcher: Matcher[AnyRef] = booleanPropertyMatcher("elem")
    val state: BooleanRef = new BooleanRef(true)

    val enabled: MatchResult = matcher(state)
    assertThat(enabled.matches).isTrue()

    state.elem = false
    val disabled: MatchResult = matcher(state)
    assertThat(disabled.matches).isFalse()
  }

  @Test
  def booleanSymbolMatcherInvokesPlainPredicate(): Unit = {
    val matcher: Matcher[AnyRef] = booleanPropertyMatcher("ready")

    val ready: MatchResult = matcher(new MethodBackedProperty(true))
    val waiting: MatchResult = matcher(new MethodBackedProperty(false))

    assertThat(ready.matches).isTrue()
    assertThat(waiting.matches).isFalse()
  }

  @Test
  def booleanSymbolMatcherInvokesJavaBeanPredicate(): Unit = {
    val matcher: Matcher[AnyRef] = booleanPropertyMatcher("ready")

    val ready: MatchResult = matcher(new BeanMethodBackedProperty(true))
    val waiting: MatchResult = matcher(new BeanMethodBackedProperty(false))

    assertThat(ready.matches).isTrue()
    assertThat(waiting.matches).isFalse()
  }

  private def booleanPropertyMatcher(propertyName: String): Matcher[AnyRef] = {
    val position: source.Position =
      source.Position("MatchersHelperTest.scala", "MatchersHelperTest.scala", 1)

    be.apply(Symbol(propertyName))(Prettifier.default, position)
  }
}

class MethodBackedProperty(private val available: Boolean) {
  def ready: Boolean = available
}

class BeanMethodBackedProperty(private val available: Boolean) {
  def isReady: Boolean = available
}

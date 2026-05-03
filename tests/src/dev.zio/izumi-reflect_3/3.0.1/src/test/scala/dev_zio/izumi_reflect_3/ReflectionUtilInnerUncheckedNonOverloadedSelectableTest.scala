/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.izumi_reflect_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReflectionUtilInnerUncheckedNonOverloadedSelectableTest {
  @Test
  def invokesNoArgumentMethodThroughUncheckedSelectable(): Unit = {
    val target: UncheckedSelectableInvocationTarget = new UncheckedSelectableInvocationTarget
    val result: Any = UncheckedSelectableInvoker.invoke(target, "noArgumentGreeting", Nil, Nil)

    assertThat(result).isEqualTo("hello from izumi")
  }

  @Test
  def invokesArgumentMethodThroughUncheckedSelectable(): Unit = {
    val target: UncheckedSelectableInvocationTarget = new UncheckedSelectableInvocationTarget
    val result: Any = UncheckedSelectableInvoker.invoke(target, "greeting", Nil, Seq("zio"))

    assertThat(result).isEqualTo("hello zio")
  }
}

final class UncheckedSelectableInvocationTarget {
  def noArgumentGreeting(): String = "hello from izumi"

  def greeting(name: String): String = s"hello $name"
}

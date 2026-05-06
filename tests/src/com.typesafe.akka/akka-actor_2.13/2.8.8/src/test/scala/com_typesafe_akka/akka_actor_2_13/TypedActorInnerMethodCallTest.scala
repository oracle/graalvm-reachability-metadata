/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import java.lang.reflect.Method

import akka.actor.TypedActor.MethodCall
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypedActorInnerMethodCallTest {
  @Test
  def invokesMethodWhenParametersAreNull(): Unit = {
    val target: MethodCallTarget = new MethodCallTarget()
    val method: Method = classOf[MethodCallTarget].getMethod("noArguments")
    val call: MethodCall = MethodCall(method, null.asInstanceOf[Array[AnyRef]])

    assertThat(call(target)).isEqualTo("no-arguments")
  }

  @Test
  def invokesMethodWhenParametersAreEmpty(): Unit = {
    val target: MethodCallTarget = new MethodCallTarget()
    val method: Method = classOf[MethodCallTarget].getMethod("emptyArguments")
    val call: MethodCall = MethodCall(method, Array.empty[AnyRef])

    assertThat(call(target)).isEqualTo("empty-arguments")
  }

  @Test
  def invokesMethodWhenParametersContainArguments(): Unit = {
    val target: MethodCallTarget = new MethodCallTarget()
    val method: Method = classOf[MethodCallTarget].getMethod("withArguments", classOf[String], classOf[Integer])
    val call: MethodCall = MethodCall(method, Array[AnyRef]("value", Integer.valueOf(42)))

    assertThat(call(target)).isEqualTo("value-42")
  }
}

class MethodCallTarget {
  def noArguments(): String = "no-arguments"

  def emptyArguments(): String = "empty-arguments"

  def withArguments(prefix: String, value: Integer): String = s"$prefix-$value"
}

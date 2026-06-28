/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import org.apache.pekko.actor.TypedActor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.annotation.nowarn

@nowarn("msg=deprecated")
class TypedActorInnerMethodCallTest {
  @Test
  def invokesNoArgumentMethodWhenParametersAreNull(): Unit = {
    val target: MethodCallTarget = new MethodCallTarget
    val method: java.lang.reflect.Method = classOf[MethodCallTarget].getMethod("noParameters")
    val call: TypedActor.MethodCall = TypedActor.MethodCall(method, null.asInstanceOf[Array[AnyRef]])

    assertThat(call(target)).isEqualTo("no-parameters")
  }

  @Test
  def invokesNoArgumentMethodWhenParametersAreEmpty(): Unit = {
    val target: MethodCallTarget = new MethodCallTarget
    val method: java.lang.reflect.Method = classOf[MethodCallTarget].getMethod("noParameters")
    val call: TypedActor.MethodCall = TypedActor.MethodCall(method, Array.empty[AnyRef])

    assertThat(call(target)).isEqualTo("no-parameters")
  }

  @Test
  def invokesMethodWithParameters(): Unit = {
    val target: MethodCallTarget = new MethodCallTarget
    val method: java.lang.reflect.Method = classOf[MethodCallTarget].getMethod("echo", classOf[String])
    val call: TypedActor.MethodCall = TypedActor.MethodCall(method, Array[AnyRef]("value"))

    assertThat(call(target)).isEqualTo("echo-value")
  }
}

final class MethodCallTarget {
  def noParameters(): String = "no-parameters"

  def echo(value: String): String = s"echo-$value"
}

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

class TypedActorInnerMethodCallTest {
  @Test
  def invokesMethodWithNullParameterArray(): Unit = {
    val target: TypedActorInnerMethodCallTarget = new TypedActorInnerMethodCallTarget
    val methodCall: TypedActor.MethodCall = TypedActor.MethodCall(
      classOf[TypedActorInnerMethodCallTarget].getMethod("noParameters"),
      null)

    val result: AnyRef = methodCall(target)

    assertThat(result).isEqualTo("no-parameters")
  }

  @Test
  def invokesMethodWithEmptyParameterArray(): Unit = {
    val target: TypedActorInnerMethodCallTarget = new TypedActorInnerMethodCallTarget
    val methodCall: TypedActor.MethodCall = TypedActor.MethodCall(
      classOf[TypedActorInnerMethodCallTarget].getMethod("noParameters"),
      Array.empty[AnyRef])

    val result: AnyRef = methodCall(target)

    assertThat(result).isEqualTo("no-parameters")
  }

  @Test
  def invokesMethodWithArguments(): Unit = {
    val target: TypedActorInnerMethodCallTarget = new TypedActorInnerMethodCallTarget
    val methodCall: TypedActor.MethodCall = TypedActor.MethodCall(
      classOf[TypedActorInnerMethodCallTarget].getMethod("format", classOf[String], classOf[java.lang.Integer]),
      Array[AnyRef]("count", Integer.valueOf(3)))

    val result: AnyRef = methodCall(target)

    assertThat(result).isEqualTo("count:3")
  }
}

final class TypedActorInnerMethodCallTarget {
  def noParameters(): String = "no-parameters"

  def format(name: String, value: java.lang.Integer): String = s"$name:$value"
}

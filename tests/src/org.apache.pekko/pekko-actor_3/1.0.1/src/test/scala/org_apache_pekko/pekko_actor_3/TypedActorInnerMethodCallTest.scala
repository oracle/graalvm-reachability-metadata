/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import org.apache.pekko.actor.TypedActor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypedActorInnerMethodCallTest {
  @Test
  def invokesNoArgumentMethodWhenParametersAreNull(): Unit = {
    val target: TypedActorInnerMethodCallTarget = new TypedActorInnerMethodCallTarget
    val method = classOf[TypedActorInnerMethodCallTarget].getMethod("noArgumentValue")
    val methodCall: TypedActor.MethodCall = TypedActor.MethodCall(method, null)

    val result: AnyRef = methodCall(target)

    assertThat(result).isEqualTo("no-argument")
    assertThat(target.invocations).isEqualTo(1)
  }

  @Test
  def invokesNoArgumentMethodWhenParametersAreEmpty(): Unit = {
    val target: TypedActorInnerMethodCallTarget = new TypedActorInnerMethodCallTarget
    val method = classOf[TypedActorInnerMethodCallTarget].getMethod("noArgumentValue")
    val methodCall: TypedActor.MethodCall = TypedActor.MethodCall(method, Array.empty[AnyRef])

    val result: AnyRef = methodCall(target)

    assertThat(result).isEqualTo("no-argument")
    assertThat(target.invocations).isEqualTo(1)
  }

  @Test
  def invokesMethodWithArgumentsWhenParametersAreProvided(): Unit = {
    val target: TypedActorInnerMethodCallTarget = new TypedActorInnerMethodCallTarget
    val method = classOf[TypedActorInnerMethodCallTarget].getMethod(
      "combine",
      classOf[String],
      classOf[java.lang.Integer])
    val methodCall: TypedActor.MethodCall = TypedActor.MethodCall(method, Array[AnyRef]("value", Integer.valueOf(7)))

    val result: AnyRef = methodCall(target)

    assertThat(result).isEqualTo("value:7")
    assertThat(target.invocations).isEqualTo(1)
  }
}

class TypedActorInnerMethodCallTarget {
  var invocations: Int = 0

  def noArgumentValue(): String = {
    invocations += 1
    "no-argument"
  }

  def combine(value: String, count: java.lang.Integer): String = {
    invocations += 1
    s"$value:$count"
  }
}

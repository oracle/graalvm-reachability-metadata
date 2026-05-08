/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import org.apache.pekko.actor.TypedActor.MethodCall
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypedActorInnerMethodCallTest {
  @Test
  def invokesNoArgumentMethodWhenParametersAreNull(): Unit = {
    val target: TypedActorMethodCallTarget = new TypedActorMethodCallTarget
    val call: MethodCall = MethodCall(
      classOf[TypedActorMethodCallTarget].getMethod("nullParametersBranch"),
      null)

    assertEquals("null-parameters", call(target))
  }

  @Test
  def invokesNoArgumentMethodWhenParametersAreEmpty(): Unit = {
    val target: TypedActorMethodCallTarget = new TypedActorMethodCallTarget
    val call: MethodCall = MethodCall(
      classOf[TypedActorMethodCallTarget].getMethod("emptyParametersBranch"),
      Array.empty[AnyRef])

    assertEquals("empty-parameters", call(target))
  }

  @Test
  def invokesMethodWithArgumentsWhenParametersArePresent(): Unit = {
    val target: TypedActorMethodCallTarget = new TypedActorMethodCallTarget
    val call: MethodCall = MethodCall(
      classOf[TypedActorMethodCallTarget].getMethod("join", classOf[String], classOf[java.lang.Integer]),
      Array[AnyRef]("value", java.lang.Integer.valueOf(7)))

    assertEquals("value-7", call(target))
  }
}

final class TypedActorMethodCallTarget {
  def nullParametersBranch(): String = "null-parameters"

  def emptyParametersBranch(): String = "empty-parameters"

  def join(prefix: String, number: java.lang.Integer): String = s"$prefix-$number"
}

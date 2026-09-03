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
  def invokesNoArgumentMethodWhenParametersAreNull(): Unit = {
    val target: TypedActorMethodCallTarget = new TypedActorMethodCallTargetImpl("null-parameters")
    val method: Method = classOf[TypedActorMethodCallTarget].getMethod("noArguments")
    val call: MethodCall = MethodCall(method, null.asInstanceOf[Array[AnyRef]])

    val result: AnyRef = call(target)

    assertThat(result).isEqualTo("null-parameters:no-arguments")
  }

  @Test
  def invokesNoArgumentMethodWhenParametersAreEmpty(): Unit = {
    val target: TypedActorMethodCallTarget = new TypedActorMethodCallTargetImpl("empty-parameters")
    val method: Method = classOf[TypedActorMethodCallTarget].getMethod("noArguments")
    val call: MethodCall = MethodCall(method, Array.empty[AnyRef])

    val result: AnyRef = call(target)

    assertThat(result).isEqualTo("empty-parameters:no-arguments")
  }

  @Test
  def invokesArgumentMethodWhenParametersArePresent(): Unit = {
    val target: TypedActorMethodCallTarget = new TypedActorMethodCallTargetImpl("present-parameters")
    val method: Method = classOf[TypedActorMethodCallTarget].getMethod(
      "withArguments",
      classOf[String],
      classOf[String])
    val call: MethodCall = MethodCall(method, Array[AnyRef]("message", "suffix"))

    val result: AnyRef = call(target)

    assertThat(result).isEqualTo("present-parameters:message:suffix")
  }
}

trait TypedActorMethodCallTarget {
  def noArguments(): String

  def withArguments(message: String, suffix: String): String
}

final class TypedActorMethodCallTargetImpl(prefix: String) extends TypedActorMethodCallTarget {
  override def noArguments(): String = s"$prefix:no-arguments"

  override def withArguments(message: String, suffix: String): String = s"$prefix:$message:$suffix"
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite
import org.apache.pekko.protobufv3.internal.MessageLite
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratedMessageLiteTest {
  @Test
  def resolvesDefaultInstanceForLiteMessageDuringSchemaCreation(): Unit = {
    val message: GeneratedMessageLiteCoverageMessage = new GeneratedMessageLiteCoverageMessage()

    val hashCode: Int = message.hashCode()

    assertThat(hashCode).isEqualTo(message.hashCode())
  }

  @Test
  def invokesGeneratedCodeReflectionHelpers(): Unit = {
    val greetingMethod: Method = GeneratedMessageLiteTest.getMethodOrDie(
      classOf[GeneratedMessageLiteReflectionTarget],
      "greeting",
      Array.empty[Class[_]]
    )

    val greeting: String = GeneratedMessageLiteTest.invokeOrDie(
      greetingMethod,
      new GeneratedMessageLiteReflectionTarget(),
      Array.empty[AnyRef]
    ).asInstanceOf[String]

    assertThat(greeting).isEqualTo("hello from generated lite helper")
  }
}

object GeneratedMessageLiteTest {
  private val GeneratedMessageLiteClass: Class[GeneratedMessageLite[_, _]] = classOf[GeneratedMessageLite[_, _]]
  private val Lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(
    GeneratedMessageLiteClass,
    MethodHandles.lookup()
  )

  private val GetMethodOrDie: MethodHandle = Lookup.findStatic(
    GeneratedMessageLiteClass,
    "getMethodOrDie",
    MethodType.methodType(classOf[Method], classOf[Class[_]], classOf[String], classOf[Array[Class[_]]])
  ).asFixedArity()

  private val InvokeOrDie: MethodHandle = Lookup.findStatic(
    GeneratedMessageLiteClass,
    "invokeOrDie",
    MethodType.methodType(classOf[Object], classOf[Method], classOf[Object], classOf[Array[Object]])
  ).asFixedArity()

  private val NewMessageInfo: MethodHandle = Lookup.findStatic(
    GeneratedMessageLiteClass,
    "newMessageInfo",
    MethodType.methodType(classOf[Object], classOf[MessageLite], classOf[String], classOf[Array[Object]])
  )

  def getMethodOrDie(targetClass: Class[_], name: String, parameterTypes: Array[Class[_]]): Method = {
    GetMethodOrDie.invokeWithArguments(targetClass, name, parameterTypes).asInstanceOf[Method]
  }

  def invokeOrDie(method: Method, target: Object, arguments: Array[AnyRef]): Object = {
    InvokeOrDie.invokeWithArguments(method, target, arguments).asInstanceOf[Object]
  }

  def newMessageInfo(defaultInstance: MessageLite, info: String, objects: Array[AnyRef]): Object = {
    NewMessageInfo.invokeWithArguments(defaultInstance, info, objects).asInstanceOf[Object]
  }
}

final class GeneratedMessageLiteCoverageMessage
  extends GeneratedMessageLite[GeneratedMessageLiteCoverageMessage, GeneratedMessageLiteCoverageBuilder] {

  override protected def dynamicMethod(
    methodToInvoke: GeneratedMessageLite.MethodToInvoke,
    argument0: Object,
    argument1: Object
  ): Object = {
    methodToInvoke match {
      case GeneratedMessageLite.MethodToInvoke.NEW_MUTABLE_INSTANCE =>
        new GeneratedMessageLiteCoverageMessage()
      case GeneratedMessageLite.MethodToInvoke.NEW_BUILDER =>
        new GeneratedMessageLiteCoverageBuilder()
      case GeneratedMessageLite.MethodToInvoke.BUILD_MESSAGE_INFO =>
        GeneratedMessageLiteTest.newMessageInfo(
          GeneratedMessageLiteCoverageMessage.defaultInstance,
          "\u0000\u0000",
          Array.empty[AnyRef]
        )
      case GeneratedMessageLite.MethodToInvoke.GET_DEFAULT_INSTANCE =>
        GeneratedMessageLiteCoverageMessage.defaultInstance
      case GeneratedMessageLite.MethodToInvoke.GET_PARSER =>
        new GeneratedMessageLite.DefaultInstanceBasedParser[GeneratedMessageLiteCoverageMessage](
          GeneratedMessageLiteCoverageMessage.defaultInstance
        )
      case GeneratedMessageLite.MethodToInvoke.GET_MEMOIZED_IS_INITIALIZED =>
        java.lang.Byte.valueOf(1.toByte)
      case GeneratedMessageLite.MethodToInvoke.SET_MEMOIZED_IS_INITIALIZED =>
        null
    }
  }
}

object GeneratedMessageLiteCoverageMessage {
  val defaultInstance: GeneratedMessageLiteCoverageMessage = new GeneratedMessageLiteCoverageMessage()
}

final class GeneratedMessageLiteCoverageBuilder
  extends GeneratedMessageLite.Builder[GeneratedMessageLiteCoverageMessage, GeneratedMessageLiteCoverageBuilder](
    GeneratedMessageLiteCoverageMessage.defaultInstance
  )

final class GeneratedMessageLiteReflectionTarget {
  def greeting(): String = "hello from generated lite helper"
}

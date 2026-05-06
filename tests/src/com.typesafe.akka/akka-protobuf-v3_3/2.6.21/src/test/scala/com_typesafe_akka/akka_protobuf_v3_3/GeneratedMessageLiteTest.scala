/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import java.lang.reflect.Method

import akka.protobufv3.internal.GeneratedMessageLite
import akka.protobufv3.internal.Parser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GeneratedMessageLiteTest {
  @Test
  def schemaCreationLoadsUnregisteredLiteDefaultInstance(): Unit = {
    val message: GeneratedMessageLiteProbeMessage = new GeneratedMessageLiteProbeMessage()

    assertThrows(classOf[RuntimeException], () => message.hashCode())

    assertSame(GeneratedMessageLiteProbeMessage.DefaultInstance, message.getDefaultInstanceForType)
  }

  @Test
  def reflectiveMethodHelpersResolveAndInvokePublicAccessors(): Unit = {
    val getMethodOrDie: Method = classOf[GeneratedMessageLite[?, ?]].getDeclaredMethod(
      "getMethodOrDie",
      classOf[Class[?]],
      classOf[String],
      classOf[Array[Class[?]]]
    )
    getMethodOrDie.setAccessible(true)

    val invokeOrDie: Method = classOf[GeneratedMessageLite[?, ?]].getDeclaredMethod(
      "invokeOrDie",
      classOf[Method],
      classOf[Object],
      classOf[Array[Object]]
    )
    invokeOrDie.setAccessible(true)

    val accessor: Method = getMethodOrDie
      .invoke(
        null,
        classOf[GeneratedMessageLiteReflectiveTarget],
        "format",
        Array[Class[?]](classOf[String])
      )
      .asInstanceOf[Method]
    val result: String = invokeOrDie
      .invoke(null, accessor, new GeneratedMessageLiteReflectiveTarget(), Array[Object]("payload"))
      .asInstanceOf[String]

    assertEquals("lite-payload", result)
  }
}

final class GeneratedMessageLiteProbeMessage
  extends GeneratedMessageLite[GeneratedMessageLiteProbeMessage, GeneratedMessageLiteProbeMessageBuilder] {
  override protected def dynamicMethod(
    method: GeneratedMessageLite.MethodToInvoke,
    arg0: Object,
    arg1: Object
  ): Object = {
    method match {
      case GeneratedMessageLite.MethodToInvoke.GET_MEMOIZED_IS_INITIALIZED => Byte.box(1.toByte)
      case GeneratedMessageLite.MethodToInvoke.SET_MEMOIZED_IS_INITIALIZED => null
      case GeneratedMessageLite.MethodToInvoke.BUILD_MESSAGE_INFO => null
      case GeneratedMessageLite.MethodToInvoke.NEW_MUTABLE_INSTANCE => new GeneratedMessageLiteProbeMessage()
      case GeneratedMessageLite.MethodToInvoke.NEW_BUILDER => new GeneratedMessageLiteProbeMessageBuilder()
      case GeneratedMessageLite.MethodToInvoke.GET_DEFAULT_INSTANCE => GeneratedMessageLiteProbeMessage.DefaultInstance
      case GeneratedMessageLite.MethodToInvoke.GET_PARSER => GeneratedMessageLiteProbeMessage.ParserInstance
    }
  }
}

object GeneratedMessageLiteProbeMessage {
  val DefaultInstance: GeneratedMessageLiteProbeMessage = new GeneratedMessageLiteProbeMessage()
  val ParserInstance: Parser[GeneratedMessageLiteProbeMessage] =
    new GeneratedMessageLite.DefaultInstanceBasedParser[GeneratedMessageLiteProbeMessage](DefaultInstance)
}

final class GeneratedMessageLiteProbeMessageBuilder
  extends GeneratedMessageLite.Builder[GeneratedMessageLiteProbeMessage, GeneratedMessageLiteProbeMessageBuilder](
    GeneratedMessageLiteProbeMessage.DefaultInstance
  )

final class GeneratedMessageLiteReflectiveTarget {
  def format(value: String): String = s"lite-$value"
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import java.util.Arrays
import java.util.List

import akka.protobufv3.internal.GeneratedMessageLite
import akka.protobufv3.internal.Parser
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageLiteToStringTest {
  @Test
  def generatedMessageLiteToStringReflectsOverDeclaredAccessors(): Unit = {
    val message: MessageLiteToStringProbeMessage = new MessageLiteToStringProbeMessage("visible-title", true)

    val rendered: String = message.toString

    assertTrue(rendered.startsWith("# "))
    assertTrue(rendered.contains("title: \"visible-title\""))
    assertTrue(rendered.contains("tags: \"alpha\""))
    assertTrue(rendered.contains("tags: \"beta\""))
  }
}

final class MessageLiteToStringProbeMessage(private val title: String, private val titlePresent: Boolean)
  extends GeneratedMessageLite[MessageLiteToStringProbeMessage, MessageLiteToStringProbeMessageBuilder] {

  def getTitle: String = title

  def hasTitle: Boolean = titlePresent

  def setTitle(value: String): MessageLiteToStringProbeMessage = this

  def getTagsList: List[String] = Arrays.asList("alpha", "beta")

  override def hashCode(): Int = System.identityHashCode(this)

  override protected def dynamicMethod(
    method: GeneratedMessageLite.MethodToInvoke,
    arg0: Object,
    arg1: Object
  ): Object = {
    method match {
      case GeneratedMessageLite.MethodToInvoke.GET_MEMOIZED_IS_INITIALIZED => Byte.box(1.toByte)
      case GeneratedMessageLite.MethodToInvoke.SET_MEMOIZED_IS_INITIALIZED => null
      case GeneratedMessageLite.MethodToInvoke.BUILD_MESSAGE_INFO => null
      case GeneratedMessageLite.MethodToInvoke.NEW_MUTABLE_INSTANCE => new MessageLiteToStringProbeMessage("", false)
      case GeneratedMessageLite.MethodToInvoke.NEW_BUILDER => new MessageLiteToStringProbeMessageBuilder()
      case GeneratedMessageLite.MethodToInvoke.GET_DEFAULT_INSTANCE => MessageLiteToStringProbeMessage.DefaultInstance
      case GeneratedMessageLite.MethodToInvoke.GET_PARSER => MessageLiteToStringProbeMessage.ParserInstance
    }
  }
}

object MessageLiteToStringProbeMessage {
  val DefaultInstance: MessageLiteToStringProbeMessage = new MessageLiteToStringProbeMessage("", false)
  val ParserInstance: Parser[MessageLiteToStringProbeMessage] =
    new GeneratedMessageLite.DefaultInstanceBasedParser[MessageLiteToStringProbeMessage](DefaultInstance)
}

final class MessageLiteToStringProbeMessageBuilder
  extends GeneratedMessageLite.Builder[MessageLiteToStringProbeMessage, MessageLiteToStringProbeMessageBuilder](
    MessageLiteToStringProbeMessage.DefaultInstance
  )

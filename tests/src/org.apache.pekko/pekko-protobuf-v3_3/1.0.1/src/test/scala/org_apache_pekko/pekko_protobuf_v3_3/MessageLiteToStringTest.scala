/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MessageLiteToStringTest {
  @Test
  def printsLiteMessageFieldsFromDeclaredMethods(): Unit = {
    val message: MessageLiteToStringCoverageMessage = new MessageLiteToStringCoverageMessage()

    val printedMessage: String = message.toString

    assertThat(printedMessage).contains("title: \"coverage\"")
  }
}

final class MessageLiteToStringCoverageMessage
  extends GeneratedMessageLite[MessageLiteToStringCoverageMessage, MessageLiteToStringCoverageBuilder] {

  def getTitle(): String = "coverage"

  def setTitle(value: String): Unit = ()

  override def hashCode(): Int = System.identityHashCode(this)

  override protected def dynamicMethod(
    methodToInvoke: GeneratedMessageLite.MethodToInvoke,
    argument0: Object,
    argument1: Object
  ): Object = {
    methodToInvoke match {
      case GeneratedMessageLite.MethodToInvoke.NEW_MUTABLE_INSTANCE =>
        new MessageLiteToStringCoverageMessage()
      case GeneratedMessageLite.MethodToInvoke.NEW_BUILDER =>
        new MessageLiteToStringCoverageBuilder()
      case GeneratedMessageLite.MethodToInvoke.GET_DEFAULT_INSTANCE =>
        MessageLiteToStringCoverageMessage.defaultInstance
      case GeneratedMessageLite.MethodToInvoke.GET_PARSER =>
        new GeneratedMessageLite.DefaultInstanceBasedParser[MessageLiteToStringCoverageMessage](
          MessageLiteToStringCoverageMessage.defaultInstance
        )
      case GeneratedMessageLite.MethodToInvoke.GET_MEMOIZED_IS_INITIALIZED =>
        java.lang.Byte.valueOf(1.toByte)
      case GeneratedMessageLite.MethodToInvoke.SET_MEMOIZED_IS_INITIALIZED =>
        null
      case GeneratedMessageLite.MethodToInvoke.BUILD_MESSAGE_INFO =>
        null
    }
  }
}

object MessageLiteToStringCoverageMessage {
  val defaultInstance: MessageLiteToStringCoverageMessage = new MessageLiteToStringCoverageMessage()
}

final class MessageLiteToStringCoverageBuilder
  extends GeneratedMessageLite.Builder[MessageLiteToStringCoverageMessage, MessageLiteToStringCoverageBuilder](
    MessageLiteToStringCoverageMessage.defaultInstance
  )

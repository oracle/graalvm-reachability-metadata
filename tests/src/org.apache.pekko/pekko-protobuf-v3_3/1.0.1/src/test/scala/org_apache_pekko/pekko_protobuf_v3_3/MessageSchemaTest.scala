/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.GeneratedMessageLite
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.assertj.core.api.ThrowableAssert.ThrowingCallable
import org.junit.jupiter.api.Test

class MessageSchemaTest {
  @Test
  def createsLiteSchemaFromGeneratedMessageInfoFields(): Unit = {
    val message: MessageSchemaReflectableMessage = new MessageSchemaReflectableMessage()

    message.setText("schema field lookup")

    assertThat(message.toByteArray).isNotEmpty()
    assertThat(message.hashCode()).isEqualTo(message.hashCode())
  }

  @Test
  def reportsGeneratedMessageInfoFieldsMissingFromMessageClass(): Unit = {
    val message: MessageSchemaMissingFieldMessage = new MessageSchemaMissingFieldMessage()

    val thrown: Throwable = catchThrowable(new ThrowingCallable {
      override def call(): Unit = message.hashCode()
    })

    assertThat(thrown).isInstanceOf(classOf[RuntimeException])
    assertThat(thrown.getMessage).contains("Field absent_")
    assertThat(thrown.getMessage).contains("not found")
  }
}

final class MessageSchemaReflectableMessage
  extends GeneratedMessageLite[MessageSchemaReflectableMessage, MessageSchemaReflectableBuilder] {
  private var `text_`: String = ""

  def setText(value: String): Unit = {
    `text_` = value
  }

  override protected def dynamicMethod(
    methodToInvoke: GeneratedMessageLite.MethodToInvoke,
    argument0: Object,
    argument1: Object
  ): Object = {
    methodToInvoke match {
      case GeneratedMessageLite.MethodToInvoke.NEW_MUTABLE_INSTANCE =>
        new MessageSchemaReflectableMessage()
      case GeneratedMessageLite.MethodToInvoke.NEW_BUILDER =>
        new MessageSchemaReflectableBuilder()
      case GeneratedMessageLite.MethodToInvoke.BUILD_MESSAGE_INFO =>
        GeneratedMessageLiteTest.newMessageInfo(
          MessageSchemaReflectableMessage.defaultInstance,
          MessageSchemaMessageInfo.SingleUtf8StringField,
          Array[AnyRef]("text_")
        )
      case GeneratedMessageLite.MethodToInvoke.GET_DEFAULT_INSTANCE =>
        MessageSchemaReflectableMessage.defaultInstance
      case GeneratedMessageLite.MethodToInvoke.GET_PARSER =>
        new GeneratedMessageLite.DefaultInstanceBasedParser[MessageSchemaReflectableMessage](
          MessageSchemaReflectableMessage.defaultInstance
        )
      case GeneratedMessageLite.MethodToInvoke.GET_MEMOIZED_IS_INITIALIZED =>
        java.lang.Byte.valueOf(1.toByte)
      case GeneratedMessageLite.MethodToInvoke.SET_MEMOIZED_IS_INITIALIZED =>
        null
    }
  }
}

object MessageSchemaReflectableMessage {
  val defaultInstance: MessageSchemaReflectableMessage = new MessageSchemaReflectableMessage()
}

final class MessageSchemaReflectableBuilder
  extends GeneratedMessageLite.Builder[MessageSchemaReflectableMessage, MessageSchemaReflectableBuilder](
    MessageSchemaReflectableMessage.defaultInstance
  )

final class MessageSchemaMissingFieldMessage
  extends GeneratedMessageLite[MessageSchemaMissingFieldMessage, MessageSchemaMissingFieldBuilder] {

  override protected def dynamicMethod(
    methodToInvoke: GeneratedMessageLite.MethodToInvoke,
    argument0: Object,
    argument1: Object
  ): Object = {
    methodToInvoke match {
      case GeneratedMessageLite.MethodToInvoke.NEW_MUTABLE_INSTANCE =>
        new MessageSchemaMissingFieldMessage()
      case GeneratedMessageLite.MethodToInvoke.NEW_BUILDER =>
        new MessageSchemaMissingFieldBuilder()
      case GeneratedMessageLite.MethodToInvoke.BUILD_MESSAGE_INFO =>
        GeneratedMessageLiteTest.newMessageInfo(
          MessageSchemaMissingFieldMessage.defaultInstance,
          MessageSchemaMessageInfo.SingleUtf8StringField,
          Array[AnyRef]("absent_")
        )
      case GeneratedMessageLite.MethodToInvoke.GET_DEFAULT_INSTANCE =>
        MessageSchemaMissingFieldMessage.defaultInstance
      case GeneratedMessageLite.MethodToInvoke.GET_PARSER =>
        new GeneratedMessageLite.DefaultInstanceBasedParser[MessageSchemaMissingFieldMessage](
          MessageSchemaMissingFieldMessage.defaultInstance
        )
      case GeneratedMessageLite.MethodToInvoke.GET_MEMOIZED_IS_INITIALIZED =>
        java.lang.Byte.valueOf(1.toByte)
      case GeneratedMessageLite.MethodToInvoke.SET_MEMOIZED_IS_INITIALIZED =>
        null
    }
  }
}

object MessageSchemaMissingFieldMessage {
  val defaultInstance: MessageSchemaMissingFieldMessage = new MessageSchemaMissingFieldMessage()
}

final class MessageSchemaMissingFieldBuilder
  extends GeneratedMessageLite.Builder[MessageSchemaMissingFieldMessage, MessageSchemaMissingFieldBuilder](
    MessageSchemaMissingFieldMessage.defaultInstance
  )

object MessageSchemaMessageInfo {
  val SingleUtf8StringField: String = "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0000\u0000\u0001\u0208"
}

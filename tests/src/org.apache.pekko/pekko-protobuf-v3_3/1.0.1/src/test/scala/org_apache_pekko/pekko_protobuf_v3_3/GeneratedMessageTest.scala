/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.DescriptorProtos
import org.apache.pekko.protobufv3.internal.Descriptors
import org.apache.pekko.protobufv3.internal.GeneratedMessage
import org.apache.pekko.protobufv3.internal.Message
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.annotation.static

class GeneratedMessageTest {
  @Test
  def readsAndWritesFieldThroughGeneratedMessageAccessorTable(): Unit = {
    val field: Descriptors.FieldDescriptor = GeneratedMessageDescriptors.CountField
    val message: GeneratedMessageCoverageMessage = new GeneratedMessageCoverageMessage(42, true)

    assertThat(message.hasField(field)).isTrue()
    assertThat(message.getField(field)).isEqualTo(42)
  }
}

final class GeneratedMessageCoverageMessage(private val `count_`: Int, private val `hasCount_`: Boolean)
  extends GeneratedMessage {

  def this() = this(0, false)

  def hasCount: Boolean = `hasCount_`

  def getCount: Int = `count_`

  override protected def internalGetFieldAccessorTable(): GeneratedMessage.FieldAccessorTable = {
    GeneratedMessageCoverageMessage.FieldAccessorTable
  }

  override def getDefaultInstanceForType(): Message = GeneratedMessageCoverageMessage.getDefaultInstance()

  override def newBuilderForType(): Message.Builder = unsupportedBuilder()

  override def toBuilder(): Message.Builder = unsupportedBuilder()

  override protected def newBuilderForType(parent: GeneratedMessage.BuilderParent): Message.Builder = unsupportedBuilder()

  private def unsupportedBuilder(): Message.Builder = {
    throw new UnsupportedOperationException("Builder creation is not required for field accessor coverage")
  }
}

object GeneratedMessageCoverageMessage {
  private val DefaultInstance: GeneratedMessageCoverageMessage = new GeneratedMessageCoverageMessage()

  val FieldAccessorTable: GeneratedMessage.FieldAccessorTable = new GeneratedMessage.FieldAccessorTable(
    GeneratedMessageDescriptors.MessageDescriptor,
    Array("Count"),
    classOf[GeneratedMessageCoverageMessage],
    classOf[GeneratedMessageCoverageBuilder]
  )

  @static
  def getDefaultInstance(): GeneratedMessageCoverageMessage = DefaultInstance
}

abstract class GeneratedMessageCoverageBuilder extends GeneratedMessage.Builder[GeneratedMessageCoverageBuilder] {
  def hasCount: Boolean

  def getCount: Int

  def setCount(value: Int): GeneratedMessageCoverageBuilder

  def clearCount(): GeneratedMessageCoverageBuilder
}

object GeneratedMessageDescriptors {
  private val MessageType: DescriptorProtos.DescriptorProto = DescriptorProtos.DescriptorProto.newBuilder()
    .setName("GeneratedMessageCoverageMessage")
    .addField(
      DescriptorProtos.FieldDescriptorProto.newBuilder()
        .setName("count")
        .setNumber(1)
        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
        .build()
    )
    .build()

  private val FileDescriptorProto: DescriptorProtos.FileDescriptorProto = DescriptorProtos.FileDescriptorProto.newBuilder()
    .setName("generated_message_test.proto")
    .setPackage("generated_message_test")
    .setSyntax("proto2")
    .addMessageType(MessageType)
    .build()

  val FileDescriptor: Descriptors.FileDescriptor = Descriptors.FileDescriptor.buildFrom(
    FileDescriptorProto,
    Array.empty[Descriptors.FileDescriptor]
  )

  val MessageDescriptor: Descriptors.Descriptor = FileDescriptor.findMessageTypeByName("GeneratedMessageCoverageMessage")

  val CountField: Descriptors.FieldDescriptor = MessageDescriptor.findFieldByName("count")
}

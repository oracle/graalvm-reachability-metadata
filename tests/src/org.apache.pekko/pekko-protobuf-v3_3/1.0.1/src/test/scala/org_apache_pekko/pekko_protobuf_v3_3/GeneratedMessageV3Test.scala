/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.DescriptorProtos
import org.apache.pekko.protobufv3.internal.Descriptors
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3
import org.apache.pekko.protobufv3.internal.Message
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.annotation.static

class GeneratedMessageV3Test {
  @Test
  def readsFieldThroughGeneratedMessageV3AccessorTable(): Unit = {
    val field: Descriptors.FieldDescriptor = GeneratedMessageV3Descriptors.CountField
    val message: GeneratedMessageV3CoverageMessage = new GeneratedMessageV3CoverageMessage(7, true)

    assertThat(message.hasField(field)).isTrue()
    assertThat(message.getField(field)).isEqualTo(7)
  }
}

final class GeneratedMessageV3CoverageMessage(private val `count_`: Int, private val `hasCount_`: Boolean)
  extends GeneratedMessageV3 {

  def this() = this(0, false)

  def hasCount: Boolean = `hasCount_`

  def getCount: Int = `count_`

  override protected def internalGetFieldAccessorTable(): GeneratedMessageV3.FieldAccessorTable = {
    GeneratedMessageV3CoverageMessage.FieldAccessorTable
  }

  override def getDefaultInstanceForType(): Message = GeneratedMessageV3CoverageMessage.getDefaultInstance()

  override def newBuilderForType(): Message.Builder = unsupportedBuilder()

  override def toBuilder(): Message.Builder = unsupportedBuilder()

  override protected def newBuilderForType(parent: GeneratedMessageV3.BuilderParent): Message.Builder = unsupportedBuilder()

  private def unsupportedBuilder(): Message.Builder = {
    throw new UnsupportedOperationException("Builder creation is not required for GeneratedMessageV3 accessor coverage")
  }
}

object GeneratedMessageV3CoverageMessage {
  private val DefaultInstance: GeneratedMessageV3CoverageMessage = new GeneratedMessageV3CoverageMessage()

  val FieldAccessorTable: GeneratedMessageV3.FieldAccessorTable = new GeneratedMessageV3.FieldAccessorTable(
    GeneratedMessageV3Descriptors.MessageDescriptor,
    Array("Count"),
    classOf[GeneratedMessageV3CoverageMessage],
    classOf[GeneratedMessageV3CoverageBuilder]
  )

  @static
  def getDefaultInstance(): GeneratedMessageV3CoverageMessage = DefaultInstance
}

abstract class GeneratedMessageV3CoverageBuilder extends GeneratedMessageV3.Builder[GeneratedMessageV3CoverageBuilder] {
  def hasCount: Boolean

  def getCount: Int

  def setCount(value: Int): GeneratedMessageV3CoverageBuilder

  def clearCount(): GeneratedMessageV3CoverageBuilder
}

object GeneratedMessageV3Descriptors {
  private val MessageType: DescriptorProtos.DescriptorProto = DescriptorProtos.DescriptorProto.newBuilder()
    .setName("GeneratedMessageV3CoverageMessage")
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
    .setName("generated_message_v3_test.proto")
    .setPackage("generated_message_v3_test")
    .setSyntax("proto2")
    .addMessageType(MessageType)
    .build()

  val FileDescriptor: Descriptors.FileDescriptor = Descriptors.FileDescriptor.buildFrom(
    FileDescriptorProto,
    Array.empty[Descriptors.FileDescriptor]
  )

  val MessageDescriptor: Descriptors.Descriptor = FileDescriptor.findMessageTypeByName("GeneratedMessageV3CoverageMessage")

  val CountField: Descriptors.FieldDescriptor = MessageDescriptor.findFieldByName("count")
}

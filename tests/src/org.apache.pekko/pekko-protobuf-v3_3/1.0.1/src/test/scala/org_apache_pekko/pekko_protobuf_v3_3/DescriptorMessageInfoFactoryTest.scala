/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import java.util.Collections
import java.util.List

import org.apache.pekko.protobufv3.internal.CodedInputStream
import org.apache.pekko.protobufv3.internal.DescriptorProtos
import org.apache.pekko.protobufv3.internal.Descriptors
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3
import org.apache.pekko.protobufv3.internal.Message
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.annotation.static

class DescriptorMessageInfoFactoryTest {
  @Test
  def buildsSchemaForGeneratedMessageWithRepeatedAndOneofMessageFields(): Unit = {
    val message: DescriptorMessageInfoFactoryCoverageMessage = new DescriptorMessageInfoFactoryCoverageMessage()

    message.mergeEmptyInput()

    assertThat(message.getChildrenCount).isZero()
    assertThat(message.hasSelected).isFalse()
  }
}

final class DescriptorMessageInfoFactoryCoverageMessage extends GeneratedMessageV3 {
  private var `children_`: List[DescriptorMessageInfoFactoryChildMessage] = Collections.emptyList()
  private var `detailCase_`: Int = 0
  private var `detail_`: Object = null

  def mergeEmptyInput(): Unit = {
    val input: CodedInputStream = CodedInputStream.newInstance(Array.empty[Byte])
    mergeFromAndMakeImmutableInternal(input, ExtensionRegistryLite.getEmptyRegistry)
  }

  def getChildren(index: Int): DescriptorMessageInfoFactoryChildMessage = `children_`.get(index)

  def getChildrenCount: Int = `children_`.size()

  def hasSelected: Boolean = `detailCase_` == 2

  def getSelected: DescriptorMessageInfoFactoryChildMessage = {
    if (`detailCase_` == 2) {
      `detail_`.asInstanceOf[DescriptorMessageInfoFactoryChildMessage]
    } else {
      DescriptorMessageInfoFactoryChildMessage.getDefaultInstance()
    }
  }

  def getNote: String = {
    if (`detailCase_` == 3) {
      `detail_`.asInstanceOf[String]
    } else {
      ""
    }
  }

  override protected def internalGetFieldAccessorTable(): GeneratedMessageV3.FieldAccessorTable = {
    DescriptorMessageInfoFactoryCoverageMessage.FieldAccessorTable
  }

  override def getDefaultInstanceForType(): Message = DescriptorMessageInfoFactoryCoverageMessage.getDefaultInstance()

  override def newBuilderForType(): Message.Builder = unsupportedBuilder()

  override def toBuilder(): Message.Builder = unsupportedBuilder()

  override protected def newBuilderForType(parent: GeneratedMessageV3.BuilderParent): Message.Builder = unsupportedBuilder()

  private def unsupportedBuilder(): Message.Builder = {
    throw new UnsupportedOperationException("Builder is not required for descriptor schema coverage")
  }
}

object DescriptorMessageInfoFactoryCoverageMessage {
  private val DefaultInstance: DescriptorMessageInfoFactoryCoverageMessage = new DescriptorMessageInfoFactoryCoverageMessage()

  val FieldAccessorTable: GeneratedMessageV3.FieldAccessorTable = new GeneratedMessageV3.FieldAccessorTable(
    DescriptorMessageInfoFactoryDescriptors.CoverageMessageDescriptor,
    Array("Children", "Selected", "Note", "Detail")
  )

  @static
  def getDefaultInstance(): DescriptorMessageInfoFactoryCoverageMessage = DefaultInstance
}

final class DescriptorMessageInfoFactoryChildMessage extends GeneratedMessageV3 {
  private var `label_`: Object = ""

  def getLabel: String = `label_`.asInstanceOf[String]

  override protected def internalGetFieldAccessorTable(): GeneratedMessageV3.FieldAccessorTable = {
    DescriptorMessageInfoFactoryChildMessage.FieldAccessorTable
  }

  override def getDefaultInstanceForType(): Message = DescriptorMessageInfoFactoryChildMessage.getDefaultInstance()

  override def newBuilderForType(): Message.Builder = unsupportedBuilder()

  override def toBuilder(): Message.Builder = unsupportedBuilder()

  override protected def newBuilderForType(parent: GeneratedMessageV3.BuilderParent): Message.Builder = unsupportedBuilder()

  private def unsupportedBuilder(): Message.Builder = {
    throw new UnsupportedOperationException("Builder is not required for descriptor schema coverage")
  }
}

object DescriptorMessageInfoFactoryChildMessage {
  private val DefaultInstance: DescriptorMessageInfoFactoryChildMessage = new DescriptorMessageInfoFactoryChildMessage()

  val FieldAccessorTable: GeneratedMessageV3.FieldAccessorTable = new GeneratedMessageV3.FieldAccessorTable(
    DescriptorMessageInfoFactoryDescriptors.ChildMessageDescriptor,
    Array("Label")
  )

  @static
  def getDefaultInstance(): DescriptorMessageInfoFactoryChildMessage = DefaultInstance
}

object DescriptorMessageInfoFactoryDescriptors {
  private val ChildMessage: DescriptorProtos.DescriptorProto = DescriptorProtos.DescriptorProto.newBuilder()
    .setName("DescriptorMessageInfoFactoryChildMessage")
    .addField(
      DescriptorProtos.FieldDescriptorProto.newBuilder()
        .setName("label")
        .setNumber(1)
        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
        .build()
    )
    .build()

  private val CoverageMessage: DescriptorProtos.DescriptorProto = DescriptorProtos.DescriptorProto.newBuilder()
    .setName("DescriptorMessageInfoFactoryCoverageMessage")
    .addOneofDecl(
      DescriptorProtos.OneofDescriptorProto.newBuilder()
        .setName("detail")
        .build()
    )
    .addField(
      DescriptorProtos.FieldDescriptorProto.newBuilder()
        .setName("children")
        .setNumber(1)
        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
        .setTypeName(".descriptor_message_info_factory.DescriptorMessageInfoFactoryChildMessage")
        .build()
    )
    .addField(
      DescriptorProtos.FieldDescriptorProto.newBuilder()
        .setName("selected")
        .setNumber(2)
        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
        .setTypeName(".descriptor_message_info_factory.DescriptorMessageInfoFactoryChildMessage")
        .setOneofIndex(0)
        .build()
    )
    .addField(
      DescriptorProtos.FieldDescriptorProto.newBuilder()
        .setName("note")
        .setNumber(3)
        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
        .setOneofIndex(0)
        .build()
    )
    .build()

  private val FileDescriptor: Descriptors.FileDescriptor = Descriptors.FileDescriptor.buildFrom(
    DescriptorProtos.FileDescriptorProto.newBuilder()
      .setName("descriptor_message_info_factory_coverage.proto")
      .setPackage("descriptor_message_info_factory")
      .setSyntax("proto3")
      .addMessageType(ChildMessage)
      .addMessageType(CoverageMessage)
      .build(),
    Array.empty[Descriptors.FileDescriptor]
  )

  val ChildMessageDescriptor: Descriptors.Descriptor = FileDescriptor.findMessageTypeByName(
    "DescriptorMessageInfoFactoryChildMessage"
  )

  val CoverageMessageDescriptor: Descriptors.Descriptor = FileDescriptor.findMessageTypeByName(
    "DescriptorMessageInfoFactoryCoverageMessage"
  )
}

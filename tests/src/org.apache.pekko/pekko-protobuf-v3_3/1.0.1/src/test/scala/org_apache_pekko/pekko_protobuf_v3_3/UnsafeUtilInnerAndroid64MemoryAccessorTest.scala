/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import java.util.Map

import org.apache.pekko.protobufv3.internal.CodedInputStream
import org.apache.pekko.protobufv3.internal.DescriptorProtos
import org.apache.pekko.protobufv3.internal.Descriptors
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3
import org.apache.pekko.protobufv3.internal.MapEntry
import org.apache.pekko.protobufv3.internal.MapField
import org.apache.pekko.protobufv3.internal.Message
import org.apache.pekko.protobufv3.internal.WireFormat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.annotation.static

class UnsafeUtilInnerAndroid64MemoryAccessorTest {
  @Test
  def readsGeneratedMapDefaultEntryThroughAndroid64StaticFieldAccessor(): Unit = {
    val message: UnsafeUtilAndroid64MemoryAccessorCoverageMessage =
      new UnsafeUtilAndroid64MemoryAccessorCoverageMessage()

    message.mergeEmptyInput()

    assertThat(message.getAttributesMap).isEmpty()
  }
}

final class UnsafeUtilAndroid64MemoryAccessorCoverageMessage extends GeneratedMessageV3 {
  private var `attributes_`: MapField[String, Integer] = MapField.emptyMapField(
    UnsafeUtilAndroid64MemoryAccessorCoverageMessage$AttributesDefaultEntryHolder.defaultEntry
  )

  def mergeEmptyInput(): Unit = {
    val input: CodedInputStream = CodedInputStream.newInstance(Array.empty[Byte])
    mergeFromAndMakeImmutableInternal(input, ExtensionRegistryLite.getEmptyRegistry)
  }

  def getAttributesMap: Map[String, Integer] = `attributes_`.getMap

  override protected def internalGetFieldAccessorTable(): GeneratedMessageV3.FieldAccessorTable = {
    UnsafeUtilAndroid64MemoryAccessorCoverageMessage.FieldAccessorTable
  }

  override def getDefaultInstanceForType(): Message = {
    UnsafeUtilAndroid64MemoryAccessorCoverageMessage.getDefaultInstance()
  }

  override def newBuilderForType(): Message.Builder = unsupportedBuilder()

  override def toBuilder(): Message.Builder = unsupportedBuilder()

  override protected def newBuilderForType(parent: GeneratedMessageV3.BuilderParent): Message.Builder = {
    unsupportedBuilder()
  }

  private def unsupportedBuilder(): Message.Builder = {
    throw new UnsupportedOperationException("Builder is not required for Android64 accessor coverage")
  }
}

object UnsafeUtilAndroid64MemoryAccessorCoverageMessage {
  private val DefaultInstance: UnsafeUtilAndroid64MemoryAccessorCoverageMessage =
    new UnsafeUtilAndroid64MemoryAccessorCoverageMessage()

  val FieldAccessorTable: GeneratedMessageV3.FieldAccessorTable = new GeneratedMessageV3.FieldAccessorTable(
    UnsafeUtilAndroid64MemoryAccessorDescriptors.CoverageMessageDescriptor,
    Array("Attributes")
  )

  @static
  def getDefaultInstance(): UnsafeUtilAndroid64MemoryAccessorCoverageMessage = DefaultInstance
}

final class UnsafeUtilAndroid64MemoryAccessorCoverageMessage$AttributesDefaultEntryHolder

object UnsafeUtilAndroid64MemoryAccessorCoverageMessage$AttributesDefaultEntryHolder {
  @static
  val defaultEntry: MapEntry[String, Integer] = MapEntry.newDefaultInstance(
    UnsafeUtilAndroid64MemoryAccessorDescriptors.AttributesEntryDescriptor,
    WireFormat.FieldType.STRING,
    "",
    WireFormat.FieldType.INT32,
    Integer.valueOf(0)
  )
}

object UnsafeUtilAndroid64MemoryAccessorDescriptors {
  private val AttributesEntry: DescriptorProtos.DescriptorProto = DescriptorProtos.DescriptorProto.newBuilder()
    .setName("AttributesEntry")
    .setOptions(
      DescriptorProtos.MessageOptions.newBuilder()
        .setMapEntry(true)
        .build()
    )
    .addField(
      DescriptorProtos.FieldDescriptorProto.newBuilder()
        .setName("key")
        .setNumber(1)
        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
        .build()
    )
    .addField(
      DescriptorProtos.FieldDescriptorProto.newBuilder()
        .setName("value")
        .setNumber(2)
        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
        .build()
    )
    .build()

  private val CoverageMessage: DescriptorProtos.DescriptorProto = DescriptorProtos.DescriptorProto.newBuilder()
    .setName("UnsafeUtilAndroid64MemoryAccessorCoverageMessage")
    .addNestedType(AttributesEntry)
    .addField(
      DescriptorProtos.FieldDescriptorProto.newBuilder()
        .setName("attributes")
        .setNumber(1)
        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
        .setTypeName(
          ".unsafe_util_android64_memory_accessor_coverage." +
            "UnsafeUtilAndroid64MemoryAccessorCoverageMessage.AttributesEntry"
        )
        .build()
    )
    .build()

  private val FileDescriptor: Descriptors.FileDescriptor = Descriptors.FileDescriptor.buildFrom(
    DescriptorProtos.FileDescriptorProto.newBuilder()
      .setName("unsafe_util_android64_memory_accessor_coverage.proto")
      .setPackage("unsafe_util_android64_memory_accessor_coverage")
      .setSyntax("proto3")
      .addMessageType(CoverageMessage)
      .build(),
    Array.empty[Descriptors.FileDescriptor]
  )

  val CoverageMessageDescriptor: Descriptors.Descriptor = FileDescriptor.findMessageTypeByName(
    "UnsafeUtilAndroid64MemoryAccessorCoverageMessage"
  )

  val AttributesEntryDescriptor: Descriptors.Descriptor = CoverageMessageDescriptor.findNestedTypeByName(
    "AttributesEntry"
  )
}

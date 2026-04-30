/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import java.util.Collections
import java.util.Map

import org.apache.pekko.protobufv3.internal.CodedInputStream
import org.apache.pekko.protobufv3.internal.DescriptorProtos
import org.apache.pekko.protobufv3.internal.Descriptors
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3
import org.apache.pekko.protobufv3.internal.Message
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.assertj.core.api.ThrowableAssert.ThrowingCallable
import org.junit.jupiter.api.Test

import scala.annotation.static

class SchemaUtilTest {
  @Test
  def looksUpMapDefaultEntryHolderForGeneratedMapFields(): Unit = {
    val message: SchemaUtilCoverageMessage = new SchemaUtilCoverageMessage()

    val thrown: Throwable = catchThrowable(new ThrowingCallable {
      override def call(): Unit = message.mergeEmptyInput()
    })

    assertThat(thrown).isInstanceOf(classOf[RuntimeException])
    assertThat(thrown).hasRootCauseInstanceOf(classOf[IllegalStateException])
    assertThat(thrown).hasStackTraceContaining("attributes")
    assertThat(thrown).hasStackTraceContaining(classOf[SchemaUtilCoverageMessage].getName)
  }
}

final class SchemaUtilCoverageMessage extends GeneratedMessageV3 {
  private var `attributes_`: Map[String, Integer] = Collections.emptyMap[String, Integer]()

  def mergeEmptyInput(): Unit = {
    val input: CodedInputStream = CodedInputStream.newInstance(Array.empty[Byte])
    mergeFromAndMakeImmutableInternal(input, ExtensionRegistryLite.getEmptyRegistry)
  }

  def getAttributesMap: Map[String, Integer] = `attributes_`

  override protected def internalGetFieldAccessorTable(): GeneratedMessageV3.FieldAccessorTable = {
    SchemaUtilCoverageMessage.FieldAccessorTable
  }

  override def getDefaultInstanceForType(): Message = SchemaUtilCoverageMessage.getDefaultInstance()

  override def newBuilderForType(): Message.Builder = unsupportedBuilder()

  override def toBuilder(): Message.Builder = unsupportedBuilder()

  override protected def newBuilderForType(parent: GeneratedMessageV3.BuilderParent): Message.Builder = unsupportedBuilder()

  private def unsupportedBuilder(): Message.Builder = {
    throw new UnsupportedOperationException("Builder is not required for schema utility coverage")
  }
}

object SchemaUtilCoverageMessage {
  private val DefaultInstance: SchemaUtilCoverageMessage = new SchemaUtilCoverageMessage()

  val FieldAccessorTable: GeneratedMessageV3.FieldAccessorTable = new GeneratedMessageV3.FieldAccessorTable(
    SchemaUtilDescriptors.CoverageMessageDescriptor,
    Array("Attributes")
  )

  @static
  def getDefaultInstance(): SchemaUtilCoverageMessage = DefaultInstance
}

final class SchemaUtilCoverageMessage$AttributesDefaultEntryHolder

object SchemaUtilDescriptors {
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
    .setName("SchemaUtilCoverageMessage")
    .addNestedType(AttributesEntry)
    .addField(
      DescriptorProtos.FieldDescriptorProto.newBuilder()
        .setName("attributes")
        .setNumber(1)
        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
        .setTypeName(".schema_util_coverage.SchemaUtilCoverageMessage.AttributesEntry")
        .build()
    )
    .build()

  private val FileDescriptor: Descriptors.FileDescriptor = Descriptors.FileDescriptor.buildFrom(
    DescriptorProtos.FileDescriptorProto.newBuilder()
      .setName("schema_util_coverage.proto")
      .setPackage("schema_util_coverage")
      .setSyntax("proto3")
      .addMessageType(CoverageMessage)
      .build(),
    Array.empty[Descriptors.FileDescriptor]
  )

  val CoverageMessageDescriptor: Descriptors.Descriptor = FileDescriptor.findMessageTypeByName(
    "SchemaUtilCoverageMessage"
  )
}

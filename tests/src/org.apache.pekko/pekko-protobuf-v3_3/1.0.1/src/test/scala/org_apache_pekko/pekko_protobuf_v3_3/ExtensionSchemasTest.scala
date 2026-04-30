/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.CodedInputStream
import org.apache.pekko.protobufv3.internal.DescriptorProtos
import org.apache.pekko.protobufv3.internal.Descriptors
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3
import org.apache.pekko.protobufv3.internal.Message
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.annotation.static

class ExtensionSchemasTest {
  @Test
  def buildsFullRuntimeSchemaForProto2GeneratedMessage(): Unit = {
    val message: ExtensionSchemasProto2Message = new ExtensionSchemasProto2Message()

    message.mergeEmptyInput()

    assertThat(message.getDescriptorForType.getFullName).isEqualTo("extension_schemas.ExtensionSchemasProto2Message")
    assertThat(message.getDefaultInstanceForType).isSameAs(ExtensionSchemasProto2Message.getDefaultInstance())
  }
}

final class ExtensionSchemasProto2Message extends GeneratedMessageV3 {
  def mergeEmptyInput(): Unit = {
    val input: CodedInputStream = CodedInputStream.newInstance(Array.empty[Byte])
    mergeFromAndMakeImmutableInternal(input, ExtensionRegistryLite.getEmptyRegistry)
  }

  override protected def internalGetFieldAccessorTable(): GeneratedMessageV3.FieldAccessorTable = {
    ExtensionSchemasProto2Message.FieldAccessorTable
  }

  override def getDefaultInstanceForType(): Message = ExtensionSchemasProto2Message.getDefaultInstance()

  override def newBuilderForType(): Message.Builder = unsupportedBuilder()

  override def toBuilder(): Message.Builder = unsupportedBuilder()

  override protected def newBuilderForType(parent: GeneratedMessageV3.BuilderParent): Message.Builder = unsupportedBuilder()

  private def unsupportedBuilder(): Message.Builder = {
    throw new UnsupportedOperationException("Builder is not required for extension schema coverage")
  }
}

object ExtensionSchemasProto2Message {
  private val DefaultInstance: ExtensionSchemasProto2Message = new ExtensionSchemasProto2Message()

  val FieldAccessorTable: GeneratedMessageV3.FieldAccessorTable = new GeneratedMessageV3.FieldAccessorTable(
    ExtensionSchemasDescriptors.MessageDescriptor,
    Array.empty[String]
  )

  @static
  def getDefaultInstance(): ExtensionSchemasProto2Message = DefaultInstance
}

object ExtensionSchemasDescriptors {
  private val Proto2Message: DescriptorProtos.DescriptorProto = DescriptorProtos.DescriptorProto.newBuilder()
    .setName("ExtensionSchemasProto2Message")
    .build()

  private val FileDescriptor: Descriptors.FileDescriptor = Descriptors.FileDescriptor.buildFrom(
    DescriptorProtos.FileDescriptorProto.newBuilder()
      .setName("extension_schemas_coverage.proto")
      .setPackage("extension_schemas")
      .setSyntax("proto2")
      .addMessageType(Proto2Message)
      .build(),
    Array.empty[Descriptors.FileDescriptor]
  )

  val MessageDescriptor: Descriptors.Descriptor = FileDescriptor.findMessageTypeByName("ExtensionSchemasProto2Message")
}

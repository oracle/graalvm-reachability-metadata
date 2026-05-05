/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import org.apache.pekko.protobufv3.internal.DescriptorProtos
import org.apache.pekko.protobufv3.internal.Descriptors
import org.apache.pekko.protobufv3.internal.Extension
import org.apache.pekko.protobufv3.internal.ExtensionRegistry
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite
import org.apache.pekko.protobufv3.internal.Message
import org.apache.pekko.protobufv3.internal.MessageLite
import org.apache.pekko.protobufv3.internal.WireFormat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExtensionRegistryLiteTest {
  @Test
  def addsFullRuntimeExtensionThroughLiteRegistryBridge(): Unit = {
    val extensionField: Descriptors.FieldDescriptor = buildScalarExtensionDescriptor()
    val fullRegistry: ExtensionRegistry = ExtensionRegistry.newInstance()
    val liteRegistry: ExtensionRegistryLite = fullRegistry
    val extension: ScalarExtension = new ScalarExtension(extensionField)

    liteRegistry.add(extension)

    val registeredExtension: ExtensionRegistry.ExtensionInfo = fullRegistry.findImmutableExtensionByNumber(
      extensionField.getContainingType,
      extensionField.getNumber)
    assertThat(registeredExtension).isNotNull
    assertThat(registeredExtension.descriptor).isSameAs(extensionField)
  }

  private def buildScalarExtensionDescriptor(): Descriptors.FieldDescriptor = {
    val targetMessage: DescriptorProtos.DescriptorProto = DescriptorProtos.DescriptorProto.newBuilder()
      .setName("Target")
      .addExtensionRange(
        DescriptorProtos.DescriptorProto.ExtensionRange.newBuilder()
          .setStart(100)
          .setEnd(200)
          .build())
      .build()
    val scalarExtension: DescriptorProtos.FieldDescriptorProto = DescriptorProtos.FieldDescriptorProto.newBuilder()
      .setName("sample_value")
      .setNumber(100)
      .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
      .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
      .setExtendee(".coverage.Target")
      .build()
    val fileProto: DescriptorProtos.FileDescriptorProto = DescriptorProtos.FileDescriptorProto.newBuilder()
      .setName("extension_registry_lite_test.proto")
      .setPackage("coverage")
      .addMessageType(targetMessage)
      .addExtension(scalarExtension)
      .build()
    val fileDescriptor: Descriptors.FileDescriptor = Descriptors.FileDescriptor.buildFrom(
      fileProto,
      Array.empty[Descriptors.FileDescriptor])

    fileDescriptor.findExtensionByName("sample_value")
  }

  private final class ScalarExtension(descriptor: Descriptors.FieldDescriptor) extends Extension[MessageLite, Integer] {
    override def getNumber: Int = descriptor.getNumber

    override def getLiteType: WireFormat.FieldType = WireFormat.FieldType.INT32

    override def isRepeated: Boolean = false

    override def getDefaultValue: Integer = Integer.valueOf(0)

    override def getMessageDefaultInstance: Message = null

    override def getDescriptor: Descriptors.FieldDescriptor = descriptor

    override protected def getExtensionType: Extension.ExtensionType = Extension.ExtensionType.IMMUTABLE

    override protected def fromReflectionType(value: AnyRef): AnyRef = value

    override protected def singularFromReflectionType(value: AnyRef): AnyRef = value

    override protected def toReflectionType(value: AnyRef): AnyRef = value

    override protected def singularToReflectionType(value: AnyRef): AnyRef = value
  }
}

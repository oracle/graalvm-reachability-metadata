/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.DescriptorProtos
import org.apache.pekko.protobufv3.internal.Descriptors
import org.apache.pekko.protobufv3.internal.ExtensionRegistry
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite
import org.apache.pekko.protobufv3.internal.GeneratedMessage
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3
import org.apache.pekko.protobufv3.internal.Message
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.annotation.static

class ExtensionRegistryLiteTest {
  @Test
  def registersFullRuntimeExtensionThroughLiteRegistryApi(): Unit = {
    val registry: ExtensionRegistryLite = ExtensionRegistryLite.newInstance()
    val extension: GeneratedMessage.GeneratedExtension[Message, java.lang.Integer] =
      GeneratedMessage.newMessageScopedGeneratedExtension[Message, java.lang.Integer](
        ExtensionRegistryLiteScopeMessage.getDefaultInstance(),
        0,
        classOf[java.lang.Integer],
        null
      )

    registry.add(extension)

    val fullRegistry: ExtensionRegistry = registry.asInstanceOf[ExtensionRegistry]
    val extensionInfo: ExtensionRegistry.ExtensionInfo = fullRegistry.findImmutableExtensionByName(
      ExtensionRegistryLiteDescriptors.ExtensionFullName
    )

    assertThat(extensionInfo).isNotNull()
    assertThat(extensionInfo.descriptor.getNumber).isEqualTo(ExtensionRegistryLiteDescriptors.ExtensionNumber)
    assertThat(extensionInfo.descriptor.getContainingType.getFullName).isEqualTo("google.protobuf.MessageOptions")
  }
}

final class ExtensionRegistryLiteScopeMessage extends GeneratedMessageV3 {
  override protected def internalGetFieldAccessorTable(): GeneratedMessageV3.FieldAccessorTable = {
    ExtensionRegistryLiteScopeMessage.FieldAccessorTable
  }

  override def getDefaultInstanceForType(): Message = ExtensionRegistryLiteScopeMessage.getDefaultInstance()

  override def newBuilderForType(): Message.Builder = unsupportedBuilder()

  override def toBuilder(): Message.Builder = unsupportedBuilder()

  override protected def newBuilderForType(parent: GeneratedMessageV3.BuilderParent): Message.Builder = unsupportedBuilder()

  private def unsupportedBuilder(): Message.Builder = {
    throw new UnsupportedOperationException("Builder is not required for extension registry coverage")
  }
}

object ExtensionRegistryLiteScopeMessage {
  private val DefaultInstance: ExtensionRegistryLiteScopeMessage = new ExtensionRegistryLiteScopeMessage()

  val FieldAccessorTable: GeneratedMessageV3.FieldAccessorTable = new GeneratedMessageV3.FieldAccessorTable(
    ExtensionRegistryLiteDescriptors.ScopeMessageDescriptor,
    Array.empty[String]
  )

  @static
  def getDefaultInstance(): ExtensionRegistryLiteScopeMessage = DefaultInstance
}

object ExtensionRegistryLiteDescriptors {
  val ExtensionNumber: Int = 51235
  private val PackageName: String = "extension_registry_lite"
  private val ExtensionName: String = "registry_int_extension"
  val ExtensionFullName: String = s"$PackageName.ExtensionRegistryLiteScopeMessage.$ExtensionName"

  private val ScopeMessage: DescriptorProtos.DescriptorProto = DescriptorProtos.DescriptorProto.newBuilder()
    .setName("ExtensionRegistryLiteScopeMessage")
    .addExtension(
      DescriptorProtos.FieldDescriptorProto.newBuilder()
        .setName(ExtensionName)
        .setNumber(ExtensionNumber)
        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
        .setExtendee(".google.protobuf.MessageOptions")
        .build()
    )
    .build()

  private val FileDescriptor: Descriptors.FileDescriptor = Descriptors.FileDescriptor.buildFrom(
    DescriptorProtos.FileDescriptorProto.newBuilder()
      .setName("extension_registry_lite_coverage.proto")
      .setPackage(PackageName)
      .setSyntax("proto2")
      .addDependency("google/protobuf/descriptor.proto")
      .addMessageType(ScopeMessage)
      .build(),
    Array(DescriptorProtos.getDescriptor)
  )

  val ScopeMessageDescriptor: Descriptors.Descriptor = FileDescriptor.findMessageTypeByName(
    "ExtensionRegistryLiteScopeMessage"
  )
}

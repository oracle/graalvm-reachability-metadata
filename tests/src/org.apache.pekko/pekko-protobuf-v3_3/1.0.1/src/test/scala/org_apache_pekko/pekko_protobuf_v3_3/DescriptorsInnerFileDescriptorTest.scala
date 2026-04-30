/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.DescriptorProtos.DescriptorProto
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Label
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Type
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FileDescriptorProto
import org.apache.pekko.protobufv3.internal.Descriptors.FileDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets

class DescriptorsInnerFileDescriptorTest {
  @Test
  def internalBuildGeneratedFileFromLoadsImportedDescriptorClass(): Unit = {
    val fileProto: FileDescriptorProto = FileDescriptorProto.newBuilder()
      .setName("dynamic_access_host.proto")
      .setPackage("dynamicaccess.host")
      .setSyntax("proto3")
      .addDependency(GeneratedDependencyDescriptor.descriptor.getName)
      .addMessageType(hostMessageProto())
      .build()

    val descriptor: FileDescriptor = FileDescriptor.internalBuildGeneratedFileFrom(
      Array(encodeDescriptorData(fileProto)),
      classOf[DescriptorsInnerFileDescriptorTest],
      Array(classOf[GeneratedDependencyDescriptor].getName),
      Array(GeneratedDependencyDescriptor.descriptor.getName)
    )

    val dependencyMessageName: String = descriptor.findMessageTypeByName("HostMessage")
      .findFieldByName("dependency")
      .getMessageType
      .getName

    assertEquals("dynamic_access_host.proto", descriptor.getName)
    assertSame(GeneratedDependencyDescriptor.descriptor, descriptor.getDependencies.get(0))
    assertEquals("DependencyMessage", dependencyMessageName)
  }

  private def hostMessageProto(): DescriptorProto = {
    val dependencyField: FieldDescriptorProto = FieldDescriptorProto.newBuilder()
      .setName("dependency")
      .setNumber(1)
      .setLabel(Label.LABEL_OPTIONAL)
      .setType(Type.TYPE_MESSAGE)
      .setTypeName(".dynamicaccess.dependency.DependencyMessage")
      .build()

    DescriptorProto.newBuilder()
      .setName("HostMessage")
      .addField(dependencyField)
      .build()
  }

  private def encodeDescriptorData(fileProto: FileDescriptorProto): String =
    new String(fileProto.toByteArray, StandardCharsets.ISO_8859_1)
}

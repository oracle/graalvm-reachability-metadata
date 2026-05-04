/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import java.nio.charset.StandardCharsets

import org.apache.pekko.protobufv3.internal.DescriptorProtos
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Label
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Type
import org.apache.pekko.protobufv3.internal.Descriptors
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DescriptorsInnerFileDescriptorTest {
  @Test
  def internalBuildGeneratedFileFromFindsPublicDependencyDescriptor(): Unit = {
    val dependentFile: DescriptorProtos.FileDescriptorProto = DescriptorProtos.FileDescriptorProto
      .newBuilder()
      .setName("file_descriptor_generated.proto")
      .setPackage("coverage")
      .setSyntax("proto3")
      .addDependency("file_descriptor_dependency.proto")
      .addMessageType(
        DescriptorProtos.DescriptorProto
          .newBuilder()
          .setName("GeneratedMessage")
          .addField(
            field("dependency", 1, Label.LABEL_OPTIONAL, Type.TYPE_MESSAGE)
              .setTypeName(".coverage.DependencyMessage")
          )
      )
      .build()

    val descriptor: Descriptors.FileDescriptor = Descriptors.FileDescriptor.internalBuildGeneratedFileFrom(
      descriptorData(dependentFile),
      classOf[DescriptorsInnerFileDescriptorTest],
      Array(classOf[FileDescriptorDependency].getName),
      Array("file_descriptor_dependency.proto")
    )

    assertThat(descriptor.getName).isEqualTo("file_descriptor_generated.proto")
    assertThat(descriptor.getDependencies).containsExactly(FileDescriptorDependency.descriptor)
    assertThat(
      descriptor
        .findMessageTypeByName("GeneratedMessage")
        .findFieldByName("dependency")
        .getMessageType
        .getFullName
    ).isEqualTo("coverage.DependencyMessage")
  }

  private def descriptorData(file: DescriptorProtos.FileDescriptorProto): Array[String] = {
    Array(new String(file.toByteArray, StandardCharsets.ISO_8859_1))
  }

  private def field(
      name: String,
      number: Int,
      label: Label,
      fieldType: Type
  ): DescriptorProtos.FieldDescriptorProto.Builder = {
    DescriptorProtos.FieldDescriptorProto
      .newBuilder()
      .setName(name)
      .setNumber(number)
      .setLabel(label)
      .setType(fieldType)
  }
}

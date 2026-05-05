/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import java.nio.charset.StandardCharsets

import org.apache.pekko.protobufv3.internal.DescriptorProtos
import org.apache.pekko.protobufv3.internal.Descriptors
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DescriptorsInnerFileDescriptorTest {
  @Test
  def generatedFileBuilderResolvesDependencyDescriptorsByGeneratedClassName(): Unit = {
    val file: DescriptorProtos.FileDescriptorProto = DescriptorProtos.FileDescriptorProto.newBuilder()
      .setName("file_descriptor_dynamic_access.proto")
      .setPackage("coverage")
      .setSyntax("proto3")
      .addDependency(FileDescriptorDependencyProbe.descriptor.getName)
      .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
        .setName("CurrentMessage")
        .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
          .setName("dependency")
          .setNumber(1)
          .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
          .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
          .setTypeName(".coverage.DependencyMessage")))
      .build()

    val descriptor: Descriptors.FileDescriptor = Descriptors.FileDescriptor.internalBuildGeneratedFileFrom(
      encode(file),
      classOf[FileDescriptorDependencyProbe],
      Array(classOf[FileDescriptorDependencyProbe].getName),
      Array(FileDescriptorDependencyProbe.descriptor.getName))

    assertThat(descriptor.getName).isEqualTo("file_descriptor_dynamic_access.proto")
    assertThat(descriptor.getDependencies).containsExactly(FileDescriptorDependencyProbe.descriptor)
    assertThat(descriptor.findMessageTypeByName("CurrentMessage")
      .findFieldByName("dependency")
      .getMessageType).isEqualTo(FileDescriptorDependencyProbe.descriptor.findMessageTypeByName("DependencyMessage"))
  }

  private def encode(file: DescriptorProtos.FileDescriptorProto): Array[String] =
    Array(new String(file.toByteArray, StandardCharsets.ISO_8859_1))
}

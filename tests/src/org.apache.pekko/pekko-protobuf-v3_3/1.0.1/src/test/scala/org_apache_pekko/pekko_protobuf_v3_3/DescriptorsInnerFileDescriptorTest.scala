/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import java.nio.charset.StandardCharsets

import org.apache.pekko.protobufv3.internal.DescriptorProtos
import org.apache.pekko.protobufv3.internal.Descriptors
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DescriptorsInnerFileDescriptorTest {
  @Test
  def buildsGeneratedFileDescriptorFromDependencyClassNames(): Unit = {
    val descriptor: Descriptors.FileDescriptor = Descriptors.FileDescriptor.internalBuildGeneratedFileFrom(
      DescriptorsInnerFileDescriptorTest.fileDescriptorData(DescriptorsInnerFileDescriptorTest.DependentFileProto),
      classOf[DescriptorsInnerFileDescriptorTest],
      Array(DescriptorsInnerFileDescriptorTest.DependencyClassName),
      Array(DescriptorsInnerFileDescriptorTest.DependencyProtoName)
    )

    val dependency: Descriptors.FileDescriptor = descriptor.getDependencies.get(0)

    assertThat(descriptor.getName).isEqualTo(DescriptorsInnerFileDescriptorTest.DependentProtoName)
    assertThat(descriptor.getDependencies).hasSize(1)
    assertThat(dependency.getName).isEqualTo(DescriptorsInnerFileDescriptorTest.DependencyProtoName)
    assertThat(dependency.findMessageTypeByName("DependencyMessage").getFields).hasSize(1)
    assertThat(descriptor.findMessageTypeByName("DependentMessage").getFields).hasSize(1)
  }
}

object DescriptorsInnerFileDescriptorTest {
  private val DependencyClassName: String =
    "org_apache_pekko.pekko_protobuf_v3_3.DescriptorsInnerFileDescriptorDependency"
  private val DependencyProtoName: String = "descriptors_inner_file_descriptor_dependency.proto"
  private val DependentProtoName: String = "descriptors_inner_file_descriptor_dependent.proto"

  private val DependentFileProto: DescriptorProtos.FileDescriptorProto =
    DescriptorProtos.FileDescriptorProto.newBuilder()
    .setName(DependentProtoName)
    .setPackage("descriptors_inner_file_descriptor")
    .setSyntax("proto3")
    .addDependency(DependencyProtoName)
    .addMessageType(
      DescriptorProtos.DescriptorProto.newBuilder()
        .setName("DependentMessage")
        .addField(
          DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName("dependency")
            .setNumber(1)
            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName(".descriptors_inner_file_descriptor.DependencyMessage")
            .build()
        )
        .build()
    )
    .build()

  private def fileDescriptorData(proto: DescriptorProtos.FileDescriptorProto): Array[String] = {
    Array(new String(proto.toByteArray, StandardCharsets.ISO_8859_1))
  }
}

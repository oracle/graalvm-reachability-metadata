/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import java.nio.charset.StandardCharsets

import akka.protobufv3.internal.DescriptorProtos
import akka.protobufv3.internal.Descriptors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DescriptorsInnerFileDescriptorTest {
  @Test
  def generatedFileBuildsDependenciesFromPublicDescriptorFields(): Unit = {
    val descriptor: Descriptors.FileDescriptor = Descriptors.FileDescriptor.internalBuildGeneratedFileFrom(
      encodedDescriptorData(ownerFileProto()),
      classOf[DescriptorsInnerFileDescriptorTest],
      Array(classOf[FileDescriptorReflectiveDependency].getName),
      Array("reflective_dependency.proto")
    )

    val ownerMessage: Descriptors.Descriptor = descriptor.findMessageTypeByName("OwnerMessage")
    val dependencyField: Descriptors.FieldDescriptor = ownerMessage.findFieldByName("dependency")

    assertEquals("reflective_subject.proto", descriptor.getName)
    assertSame(FileDescriptorReflectiveDependency.descriptor, descriptor.getDependencies.get(0))
    assertSame(
      FileDescriptorReflectiveDependency.descriptor.findMessageTypeByName("DependencyMessage"),
      dependencyField.getMessageType
    )
  }

  private def ownerFileProto(): DescriptorProtos.FileDescriptorProto = {
    DescriptorProtos.FileDescriptorProto.newBuilder()
      .setName("reflective_subject.proto")
      .setPackage("coverage.dynamic")
      .addDependency("reflective_dependency.proto")
      .addMessageType(
        DescriptorProtos.DescriptorProto.newBuilder()
          .setName("OwnerMessage")
          .addField(
            DescriptorProtos.FieldDescriptorProto.newBuilder()
              .setName("dependency")
              .setNumber(1)
              .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
              .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
              .setTypeName(".coverage.dynamic.DependencyMessage")
              .build()
          )
          .build()
      )
      .build()
  }

  private def encodedDescriptorData(proto: DescriptorProtos.FileDescriptorProto): Array[String] = {
    Array(new String(proto.toByteArray, StandardCharsets.ISO_8859_1))
  }
}

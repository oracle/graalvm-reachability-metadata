/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13

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
      Array(classOf[GeneratedDescriptorDependency].getName),
      Array("coverage/dependency.proto")
    )

    val ownerMessage: Descriptors.Descriptor = descriptor.findMessageTypeByName("OwnerMessage")
    val dependencyField: Descriptors.FieldDescriptor = ownerMessage.findFieldByName("dependency")

    assertEquals("coverage/owner.proto", descriptor.getName)
    assertSame(GeneratedDescriptorDependency.descriptor, descriptor.getDependencies.get(0))
    assertSame(
      GeneratedDescriptorDependency.descriptor.findMessageTypeByName("DependencyMessage"),
      dependencyField.getMessageType
    )
  }

  private def ownerFileProto(): DescriptorProtos.FileDescriptorProto = {
    DescriptorProtos.FileDescriptorProto.newBuilder()
      .setName("coverage/owner.proto")
      .setPackage("coverage.owner")
      .addDependency("coverage/dependency.proto")
      .addMessageType(
        DescriptorProtos.DescriptorProto.newBuilder()
          .setName("OwnerMessage")
          .addField(
            DescriptorProtos.FieldDescriptorProto.newBuilder()
              .setName("dependency")
              .setNumber(1)
              .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
              .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
              .setTypeName(".coverage.dependency.DependencyMessage")
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

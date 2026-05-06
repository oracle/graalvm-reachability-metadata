/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import java.nio.charset.StandardCharsets

import akka.protobufv3.internal.DescriptorProtos.DescriptorProto
import akka.protobufv3.internal.DescriptorProtos.FieldDescriptorProto
import akka.protobufv3.internal.DescriptorProtos.FileDescriptorProto
import akka.protobufv3.internal.Descriptors.FileDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

import scala.annotation.static

class DescriptorsInnerFileDescriptorTest {
  @Test
  def generatedFileBuilderLoadsDependencyDescriptorFromGeneratedProtoClass(): Unit = {
    val descriptor: FileDescriptor = FileDescriptor.internalBuildGeneratedFileFrom(
      descriptorData(generatedFileProto),
      classOf[DescriptorsInnerFileDescriptorTest],
      Array(classOf[GeneratedDependencyProto].getName),
      Array(GeneratedDependencyProto.descriptor.getName)
    )

    assertEquals("coverage/uses_dependency.proto", descriptor.getName)
    assertEquals(1, descriptor.getDependencies.size())
    assertSame(GeneratedDependencyProto.descriptor, descriptor.getDependencies.get(0))
    assertEquals("UsesDependency", descriptor.getMessageTypes.get(0).getName)
  }

  private def generatedFileProto: FileDescriptorProto = FileDescriptorProto
    .newBuilder()
    .setName("coverage/uses_dependency.proto")
    .setPackage("coverage.generated")
    .addDependency(GeneratedDependencyProto.descriptor.getName)
    .addMessageType(
      DescriptorProto
        .newBuilder()
        .setName("UsesDependency")
        .addField(
          FieldDescriptorProto
            .newBuilder()
            .setName("dependency")
            .setNumber(1)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName(".coverage.dependency.DependencyMessage")
        )
    )
    .build()

  private def descriptorData(proto: FileDescriptorProto): Array[String] = Array(
    new String(proto.toByteArray, StandardCharsets.ISO_8859_1)
  )
}

final class GeneratedDependencyProto private ()

object GeneratedDependencyProto {
  @static val descriptor: FileDescriptor = FileDescriptor.buildFrom(
    FileDescriptorProto
      .newBuilder()
      .setName("coverage/dependency.proto")
      .setPackage("coverage.dependency")
      .addMessageType(
        DescriptorProto
          .newBuilder()
          .setName("DependencyMessage")
          .addField(
            FieldDescriptorProto
              .newBuilder()
              .setName("value")
              .setNumber(1)
              .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
              .setType(FieldDescriptorProto.Type.TYPE_STRING)
          )
      )
      .build(),
    Array.empty[FileDescriptor]
  )
}

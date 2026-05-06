/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import akka.protobufv3.internal.DescriptorProtos
import akka.protobufv3.internal.Descriptors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedMessageV3Test {
  @Test
  def reflectiveFieldAccessorsResolveAndInvokeGeneratedAccessors(): Unit = {
    val message: DescriptorProtos.FileDescriptorProto = DescriptorProtos.FileDescriptorProto
      .newBuilder()
      .setName("sample.proto")
      .setPackage("example")
      .addDependency("common.proto")
      .setOptions(
        DescriptorProtos.FileOptions
          .newBuilder()
          .setJavaPackage("example.generated")
          .build()
      )
      .build()
    val descriptor: Descriptors.Descriptor = DescriptorProtos.FileDescriptorProto.getDescriptor
    val nameField: Descriptors.FieldDescriptor = descriptor.findFieldByName("name")
    val dependencyField: Descriptors.FieldDescriptor = descriptor.findFieldByName("dependency")
    val optionsField: Descriptors.FieldDescriptor = descriptor.findFieldByName("options")

    assertTrue(message.hasField(nameField))
    assertEquals("sample.proto", message.getField(nameField))
    assertEquals(1, message.getRepeatedFieldCount(dependencyField))
    assertEquals("common.proto", message.getRepeatedField(dependencyField, 0))
    assertSame(message.getOptions, message.getField(optionsField))

    val builder: DescriptorProtos.FileDescriptorProto.Builder = message.toBuilder
    builder.setField(nameField, "updated.proto")

    assertEquals("updated.proto", builder.build().getName)
  }
}

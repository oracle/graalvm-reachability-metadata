/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13

import akka.protobufv3.internal.DescriptorProtos
import akka.protobufv3.internal.Descriptors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedMessageV3Test {
  @Test
  def descriptorProtoReflectionApiUsesGeneratedAccessors(): Unit = {
    val descriptor: Descriptors.Descriptor = DescriptorProtos.FieldDescriptorProto.getDescriptor
    val nameField: Descriptors.FieldDescriptor = descriptor.findFieldByName("name")
    val numberField: Descriptors.FieldDescriptor = descriptor.findFieldByName("number")
    val typeField: Descriptors.FieldDescriptor = descriptor.findFieldByName("type")

    val builder: DescriptorProtos.FieldDescriptorProto.Builder = DescriptorProtos.FieldDescriptorProto.newBuilder()
    assertFalse(builder.hasField(nameField))

    builder.setField(nameField, "field_name")
    builder.setField(numberField, Integer.valueOf(7))
    builder.setField(typeField, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32.getValueDescriptor)

    assertTrue(builder.hasField(nameField))
    assertEquals("field_name", builder.getField(nameField))

    val message: DescriptorProtos.FieldDescriptorProto = builder.build()
    assertTrue(message.hasField(nameField))
    assertEquals("field_name", message.getField(nameField))
    assertEquals(Integer.valueOf(7), message.getAllFields.get(numberField))
    assertEquals(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32.getValueDescriptor, message.getField(typeField))
  }
}

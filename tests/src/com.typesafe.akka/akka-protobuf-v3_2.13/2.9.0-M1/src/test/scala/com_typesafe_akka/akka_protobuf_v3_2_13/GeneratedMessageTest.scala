/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13

import akka.protobufv3.internal.DescriptorProtos
import akka.protobufv3.internal.Descriptors
import akka.protobufv3.internal.GeneratedMessage
import akka.protobufv3.internal.Message
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class GeneratedMessageTest {
  @Test
  def enumExtensionDefaultValueUsesGeneratedEnumAccessors(): Unit = {
    val extension: GeneratedMessage.GeneratedExtension[Message, DescriptorProtos.FieldDescriptorProto.Type] =
      GeneratedMessage.newFileScopedGeneratedExtension[Message, DescriptorProtos.FieldDescriptorProto.Type](
        classOf[DescriptorProtos.FieldDescriptorProto.Type],
        null
      )
    val enumField: Descriptors.FieldDescriptor = DescriptorProtos.FieldDescriptorProto.getDescriptor.findFieldByName("type")

    extension.internalInit(enumField)
    val defaultValue: DescriptorProtos.FieldDescriptorProto.Type = extension.getDefaultValue

    assertEquals(Descriptors.FieldDescriptor.JavaType.ENUM, enumField.getJavaType)
    assertSame(DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE, defaultValue)
  }
}

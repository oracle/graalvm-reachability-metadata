/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import akka.protobufv3.internal.DescriptorProtos
import akka.protobufv3.internal.Descriptors
import akka.protobufv3.internal.GeneratedMessage
import akka.protobufv3.internal.Message
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class GeneratedMessageTest {
  @Test
  def enumGeneratedExtensionUsesGeneratedEnumAccessors(): Unit = {
    val extension: GeneratedMessage.GeneratedExtension[Message, DescriptorProtos.FieldOptions.JSType] =
      GeneratedMessage.newFileScopedGeneratedExtension(
        classOf[DescriptorProtos.FieldOptions.JSType],
        null
      )
    val field: Descriptors.FieldDescriptor = DescriptorProtos.FieldOptions.getDescriptor.findFieldByName("jstype")

    extension.internalInit(field)

    val defaultValue: DescriptorProtos.FieldOptions.JSType = extension.getDefaultValue
    assertSame(DescriptorProtos.FieldOptions.JSType.JS_NORMAL, defaultValue)
    assertEquals(field.getNumber, extension.getNumber)
  }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13

import akka.protobufv3.internal.Descriptors
import akka.protobufv3.internal.GeneratedMessage
import akka.protobufv3.internal.Message
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class GeneratedMessageAnonymous4Test {
  @Test
  def fileScopedExtensionLoadsDescriptorFromGeneratedDescriptorClass(): Unit = {
    val extension: GeneratedMessage.GeneratedExtension[Message, GeneratedDescriptorDependency] =
      GeneratedMessage.newFileScopedGeneratedExtension(
        classOf[GeneratedDescriptorDependency],
        null,
        classOf[GeneratedDescriptorDependency].getName,
        "covered_extension"
      )

    val descriptor: Descriptors.FieldDescriptor = extension.getDescriptor

    assertEquals("covered_extension", descriptor.getName)
    assertEquals(100, descriptor.getNumber)
    assertEquals(Descriptors.FieldDescriptor.JavaType.STRING, descriptor.getJavaType)
    assertSame(GeneratedDescriptorDependency.descriptor, descriptor.getFile)
  }
}

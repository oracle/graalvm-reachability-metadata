/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import akka.protobufv3.internal.Descriptors
import akka.protobufv3.internal.GeneratedMessage
import akka.protobufv3.internal.Message
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class GeneratedMessageAnonymous4Test {
  @Test
  def fileScopedGeneratedExtensionLoadsDescriptorFromPublicDescriptorField(): Unit = {
    val extension: GeneratedMessage.GeneratedExtension[Message, java.lang.Integer] =
      GeneratedMessage.newFileScopedGeneratedExtension(
        classOf[FileDescriptorReflectiveDependency],
        null,
        classOf[FileDescriptorReflectiveDependency].getName,
        "probe_extension"
      )

    val descriptor: Descriptors.FieldDescriptor = extension.getDescriptor

    assertEquals("probe_extension", descriptor.getName)
    assertEquals(100, extension.getNumber)
    assertSame(FileDescriptorReflectiveDependency.descriptor, descriptor.getFile)
  }
}

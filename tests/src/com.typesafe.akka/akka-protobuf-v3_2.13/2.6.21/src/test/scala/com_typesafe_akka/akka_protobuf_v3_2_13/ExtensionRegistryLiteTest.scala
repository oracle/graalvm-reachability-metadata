/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13

import akka.protobufv3.internal.ExtensionLite
import akka.protobufv3.internal.ExtensionRegistry
import akka.protobufv3.internal.ExtensionRegistryLite
import akka.protobufv3.internal.GeneratedMessage
import akka.protobufv3.internal.Message
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ExtensionRegistryLiteTest {
  @Test
  def addFullRuntimeExtensionThroughLiteApiRegistersItInFullRegistry(): Unit = {
    val extension: GeneratedMessage.GeneratedExtension[Message, GeneratedDescriptorDependency] =
      GeneratedMessage.newFileScopedGeneratedExtension(
        classOf[GeneratedDescriptorDependency],
        null,
        classOf[GeneratedDescriptorDependency].getName,
        "covered_extension"
      )
    val registry: ExtensionRegistryLite = ExtensionRegistry.newInstance()

    registry.add(extension: ExtensionLite[Message, GeneratedDescriptorDependency])

    val fullRegistry: ExtensionRegistry = registry.asInstanceOf[ExtensionRegistry]
    val descriptor = extension.getDescriptor
    val infoByName = fullRegistry.findMutableExtensionByName(descriptor.getFullName)
    assertNotNull(infoByName)
    assertSame(descriptor, infoByName.descriptor)
    assertSame(infoByName, fullRegistry.findMutableExtensionByNumber(descriptor.getContainingType, descriptor.getNumber))
  }
}

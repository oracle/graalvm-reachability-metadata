/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.Descriptors
import org.apache.pekko.protobufv3.internal.Extension
import org.apache.pekko.protobufv3.internal.ExtensionLite
import org.apache.pekko.protobufv3.internal.ExtensionRegistry
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite
import org.apache.pekko.protobufv3.internal.Message
import org.apache.pekko.protobufv3.internal.WireFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtensionRegistryLiteTest {
  @Test
  def addsFullExtensionThroughLiteRegistryApi(): Unit = {
    val extension: StringExtension = new StringExtension()
    val extensionLite: ExtensionLite[Message, String] = extension
    val registry: ExtensionRegistryLite = ExtensionRegistryLite.newInstance()

    registry.add(extensionLite)

    assertTrue(registry.isInstanceOf[ExtensionRegistry])
    val fullRegistry: ExtensionRegistry = registry.asInstanceOf[ExtensionRegistry]
    val extensionInfo: ExtensionRegistry.ExtensionInfo =
      fullRegistry.findExtensionByNumber(extension.getDescriptor.getContainingType, extension.getNumber)

    assertNotNull(extensionInfo)
    assertSame(extension.getDescriptor, extensionInfo.descriptor)
    assertEquals("dynamicaccess.generatedmessageanonymous4.host_extension", extensionInfo.descriptor.getFullName)
  }

  private final class StringExtension extends Extension[Message, String] {
    override def getNumber: Int = 100

    override def getLiteType: WireFormat.FieldType = WireFormat.FieldType.STRING

    override def isRepeated: Boolean = false

    override def getDefaultValue: String = ""

    override def getMessageDefaultInstance: Message = null

    override def getDescriptor: Descriptors.FieldDescriptor =
      GeneratedMessageAnonymous4Descriptor.descriptor.findExtensionByName("host_extension")

    override protected def getExtensionType: Extension.ExtensionType = Extension.ExtensionType.IMMUTABLE

    override protected def fromReflectionType(value: AnyRef): AnyRef = value

    override protected def singularFromReflectionType(value: AnyRef): AnyRef = value

    override protected def toReflectionType(value: AnyRef): AnyRef = value

    override protected def singularToReflectionType(value: AnyRef): AnyRef = value
  }
}

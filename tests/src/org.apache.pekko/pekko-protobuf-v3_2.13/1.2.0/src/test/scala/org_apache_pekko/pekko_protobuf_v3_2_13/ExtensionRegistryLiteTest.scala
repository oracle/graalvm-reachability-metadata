/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import org.apache.pekko.protobufv3.internal.Descriptors
import org.apache.pekko.protobufv3.internal.Extension
import org.apache.pekko.protobufv3.internal.ExtensionLite
import org.apache.pekko.protobufv3.internal.ExtensionRegistry
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite
import org.apache.pekko.protobufv3.internal.Message
import org.apache.pekko.protobufv3.internal.MessageLite
import org.apache.pekko.protobufv3.internal.WireFormat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExtensionRegistryLiteTest {
  @Test
  def addExtensionLiteDispatchesFullExtensionThroughRegistryReflection(): Unit = {
    val registry: ExtensionRegistryLite = ExtensionRegistry.newInstance()
    val extension: Proto1Extension = new Proto1Extension
    val liteExtension: ExtensionLite[_ <: MessageLite, String] = extension

    registry.add(liteExtension)

    assertThat(registry).isInstanceOf(classOf[ExtensionRegistry])
    assertThat(extension.extensionTypeLookupCount).isPositive
  }

  private final class Proto1Extension extends Extension[Message, String] {
    private var extensionTypeLookups: Int = 0

    def extensionTypeLookupCount: Int = extensionTypeLookups

    override def getNumber: Int = 12345

    override def getLiteType: WireFormat.FieldType = WireFormat.FieldType.STRING

    override def isRepeated: Boolean = false

    override def getDefaultValue: String = "default"

    override def getMessageDefaultInstance: Message = null

    override def getDescriptor: Descriptors.FieldDescriptor =
      throw new AssertionError("PROTO1 extensions do not require descriptors when registered")

    override protected def getExtensionType: Extension.ExtensionType = {
      extensionTypeLookups += 1
      Extension.ExtensionType.PROTO1
    }

    override protected def fromReflectionType(value: AnyRef): AnyRef = value

    override protected def singularFromReflectionType(value: AnyRef): AnyRef = value

    override protected def toReflectionType(value: AnyRef): AnyRef = value

    override protected def singularToReflectionType(value: AnyRef): AnyRef = value
  }
}

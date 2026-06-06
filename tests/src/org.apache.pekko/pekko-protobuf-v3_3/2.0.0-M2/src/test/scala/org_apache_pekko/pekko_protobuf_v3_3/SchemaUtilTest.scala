/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType

import org.apache.pekko.protobufv3.internal.GeneratedMessageV3
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SchemaUtilTest {
  @Test
  def mapSchemaDiscoveryLoadsGeneratedDefaultEntryHolder(): Unit = {
    val message: SchemaUtilMapFieldProbe = new SchemaUtilMapFieldProbe()

    message.initializeEmptyMapSchema()
    val messageInfo: AnyRef = descriptorMessageInfoFactoryMessageInfoFor.invokeWithArguments(
      descriptorMessageInfoFactoryGetInstance.invokeWithArguments(),
      classOf[SchemaUtilMapFieldProbe]
    ).asInstanceOf[AnyRef]

    val entriesField = message.getDescriptorForType.findFieldByName("entries")
    assertThat(messageInfo).isNotNull
    assertThat(entriesField.isMapField).isTrue()
    assertThat(message.getEntriesMap).isEmpty()
  }

  private def descriptorMessageInfoFactoryGetInstance: MethodHandle = {
    descriptorMessageInfoFactoryLookup.findStatic(
      descriptorMessageInfoFactoryClass,
      "getInstance",
      methodType(descriptorMessageInfoFactoryClass)
    )
  }

  private def descriptorMessageInfoFactoryMessageInfoFor: MethodHandle = {
    descriptorMessageInfoFactoryLookup.findVirtual(
      descriptorMessageInfoFactoryClass,
      "messageInfoFor",
      methodType(messageInfoClass, classOf[Class[_]])
    )
  }

  private def descriptorMessageInfoFactoryLookup: MethodHandles.Lookup = {
    MethodHandles.privateLookupIn(descriptorMessageInfoFactoryClass, MethodHandles.lookup())
  }

  private def descriptorMessageInfoFactoryClass: Class[_] = {
    Class.forName(
      "org.apache.pekko.protobufv3.internal.DescriptorMessageInfoFactory",
      true,
      classOf[GeneratedMessageV3].getClassLoader
    )
  }

  private def messageInfoClass: Class[_] = {
    Class.forName(
      "org.apache.pekko.protobufv3.internal.MessageInfo",
      true,
      classOf[GeneratedMessageV3].getClassLoader
    )
  }
}

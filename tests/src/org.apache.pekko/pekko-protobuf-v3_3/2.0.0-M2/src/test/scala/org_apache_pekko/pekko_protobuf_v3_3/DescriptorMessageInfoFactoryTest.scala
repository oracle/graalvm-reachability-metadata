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

class DescriptorMessageInfoFactoryTest {
  @Test
  def schemaDiscoveryHandlesGeneratedMessageFieldsOneofsAndRepeatedMessages(): Unit = {
    val message: DescriptorMessageInfoFactoryProbe = new DescriptorMessageInfoFactoryProbe()

    message.initializeEmptyPayloadSchema()

    assertThat(message.getDescriptorForType.findFieldByName("regular_text").getName).isEqualTo("regular_text")
    assertThat(message.getDescriptorForType.findFieldByName("repeated_child").isRepeated).isTrue()
    assertThat(message.getMessageChoice).isSameAs(DescriptorMessageInfoFactoryProbe.Child.getDefaultInstance)
    assertThat(message.getRepeatedChildList).isEmpty()
  }

  @Test
  def descriptorMessageInfoFactoryDiscoversGeneratedMessageLayout(): Unit = {
    val factory: AnyRef = descriptorMessageInfoFactoryGetInstance.invokeWithArguments().asInstanceOf[AnyRef]
    val messageInfo: AnyRef = descriptorMessageInfoFactoryMessageInfoFor.invokeWithArguments(
      factory,
      classOf[DescriptorMessageInfoFactoryProbe]
    ).asInstanceOf[AnyRef]

    assertThat(messageInfo).isNotNull
    assertThat(DescriptorMessageInfoFactoryProbe.getDefaultInstance.getDescriptorForType
      .findFieldByName("regular_text").getName).isEqualTo("regular_text")
    assertThat(DescriptorMessageInfoFactoryProbe.getDefaultInstance.getDescriptorForType
      .findFieldByName("repeated_child").isRepeated).isTrue()
    assertThat(DescriptorMessageInfoFactoryProbe.getDefaultInstance.getDescriptorForType
      .findFieldByName("message_choice").getRealContainingOneof.getName).isEqualTo("choice")
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

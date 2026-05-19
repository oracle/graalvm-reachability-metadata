/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType

import org.apache.pekko.protobufv3.internal.GeneratedMessageV3
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DescriptorMessageInfoFactoryTest {
  @Test
  def generatedMessageSchemaUsesDescriptorBackedReflection(): Unit = {
    val message: DescriptorMessageInfoFactoryCoverageMessage =
      DescriptorMessageInfoFactoryCoverageMessage.getDefaultInstance

    message.parseEmptyInputThroughSchema()

    assertThat(message.getDescriptorForType.getFullName).isEqualTo("coverage.CoverageMessage")
    assertThat(message.getChildrenCount).isZero()
    assertThat(message.getChosen)
      .isSameAs(DescriptorMessageInfoFactoryCoverageNested.getDefaultInstance)
  }

  @Test
  def descriptorMessageInfoFactoryDiscoversGeneratedMessageLayout(): Unit = {
    val factory: AnyRef = descriptorMessageInfoFactoryGetInstance.invokeWithArguments().asInstanceOf[AnyRef]
    val messageInfo: AnyRef = descriptorMessageInfoFactoryMessageInfoFor.invokeWithArguments(
      factory,
      classOf[DescriptorMessageInfoFactoryCoverageMessage]
    ).asInstanceOf[AnyRef]

    assertThat(messageInfo).isNotNull
    assertThat(DescriptorMessageInfoFactoryCoverageMessage.getDefaultInstance.getDescriptorForType
      .findFieldByName("children").isRepeated).isTrue()
    assertThat(DescriptorMessageInfoFactoryCoverageMessage.getDefaultInstance.getDescriptorForType
      .findFieldByName("chosen").getRealContainingOneof.getName).isEqualTo("choice")
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

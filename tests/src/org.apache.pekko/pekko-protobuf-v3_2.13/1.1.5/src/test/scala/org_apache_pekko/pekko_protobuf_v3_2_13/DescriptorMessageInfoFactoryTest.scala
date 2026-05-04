/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DescriptorMessageInfoFactoryTest {
  @Test
  def schemaDiscoveryHandlesGeneratedMessageFieldsOneofsAndRepeatedMessages(): Unit = {
    val message: DescriptorMessageInfoFactoryProbe = new DescriptorMessageInfoFactoryProbe()

    message.initializeEmptyPayloadSchema()

    assertThat(message.getDescriptorForType.findFieldByName("regular_text").getName).isEqualTo("regular_text")
    assertThat(message.getDescriptorForType.findFieldByName("repeated_child").isRepeated).isTrue()
    assertThat(message.getMessageChoice).isSameAs(DescriptorMessageInfoFactoryProbeChild.getDefaultInstance)
    assertThat(message.getRepeatedChildList).isEmpty()
  }
}

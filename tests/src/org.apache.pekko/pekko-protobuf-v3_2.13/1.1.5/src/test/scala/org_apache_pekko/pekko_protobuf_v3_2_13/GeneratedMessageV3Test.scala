/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import org.apache.pekko.protobufv3.internal.Descriptors
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratedMessageV3Test {
  @Test
  def generatedMessageV3FieldAccessorsUseGeneratedAccessMethods(): Unit = {
    val field: Descriptors.FieldDescriptor = GeneratedMessageV3Probe.getDefaultInstance
      .getDescriptorForType
      .findFieldByName("quantity")
    val message: GeneratedMessageV3Probe = GeneratedMessageV3Probe.newBuilder()
      .setQuantity(7)
      .build()

    assertThat(message.hasField(field)).isTrue()
    assertThat(message.getField(field)).isEqualTo(7)

    val builder: GeneratedMessageV3Probe.Builder = message.toBuilder
    builder.setField(field, 11)

    assertThat(builder.hasField(field)).isTrue()
    assertThat(builder.getField(field)).isEqualTo(11)

    builder.clearField(field)

    assertThat(builder.hasField(field)).isFalse()
    assertThat(builder.build().getQuantity).isEqualTo(0)
  }
}

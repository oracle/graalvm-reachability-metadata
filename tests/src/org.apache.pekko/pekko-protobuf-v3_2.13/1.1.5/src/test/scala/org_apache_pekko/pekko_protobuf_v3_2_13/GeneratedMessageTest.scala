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

class GeneratedMessageTest {
  @Test
  def generatedMessageFieldAccessorsUseGeneratedAccessMethods(): Unit = {
    val field: Descriptors.FieldDescriptor = GeneratedMessageProbe.getDefaultInstance
      .getDescriptorForType
      .findFieldByName("quantity")
    val message: GeneratedMessageProbe = GeneratedMessageProbe.newBuilder()
      .setQuantity(7)
      .build()

    assertThat(message.hasField(field)).isTrue()
    assertThat(message.getField(field)).isEqualTo(7)

    val builder: GeneratedMessageProbe.Builder = message.toBuilder
    builder.setField(field, 11)

    assertThat(builder.hasField(field)).isTrue()
    assertThat(builder.getField(field)).isEqualTo(11)
    assertThat(builder.build().getQuantity).isEqualTo(11)
  }
}

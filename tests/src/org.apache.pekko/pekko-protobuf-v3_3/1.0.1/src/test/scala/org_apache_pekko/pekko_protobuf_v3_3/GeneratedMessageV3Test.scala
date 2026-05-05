/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.Descriptors
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratedMessageV3Test {
  @Test
  def fieldAccessorTableInvokesGeneratedAccessors(): Unit = {
    val numberField: Descriptors.FieldDescriptor = GeneratedMessageV3AccessorProbe.getDescriptor.findFieldByName("number")
    val builder: GeneratedMessageV3AccessorProbe.Builder = GeneratedMessageV3AccessorProbe.newBuilder()

    assertThat(builder.hasField(numberField)).isFalse()

    builder.setField(numberField, Int.box(42))

    assertThat(builder.hasField(numberField)).isTrue()
    assertThat(builder.getField(numberField)).isEqualTo(42)

    val message: GeneratedMessageV3AccessorProbe = builder.build()

    assertThat(message.hasField(numberField)).isTrue()
    assertThat(message.getField(numberField)).isEqualTo(42)

    builder.clearField(numberField)

    assertThat(builder.hasField(numberField)).isFalse()
  }
}

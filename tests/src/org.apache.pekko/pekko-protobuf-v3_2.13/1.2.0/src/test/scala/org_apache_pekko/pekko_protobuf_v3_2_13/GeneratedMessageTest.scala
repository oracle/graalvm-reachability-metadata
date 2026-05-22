/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import org.apache.pekko.protobufv3.internal.{Any => PekkoAny}
import org.apache.pekko.protobufv3.internal.ByteString
import org.apache.pekko.protobufv3.internal.Descriptors.FieldDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeneratedMessageTest {
  @Test
  def descriptorFieldAccessInvokesGeneratedAccessors(): Unit = {
    val payload: ByteString = ByteString.copyFromUtf8("coverage-payload")
    val message: PekkoAny = PekkoAny.newBuilder()
      .setTypeUrl("type.googleapis.com/coverage.Payload")
      .setValue(payload)
      .build()
    val typeUrlField: FieldDescriptor = PekkoAny.getDescriptor.findFieldByName("type_url")
    val valueField: FieldDescriptor = PekkoAny.getDescriptor.findFieldByName("value")

    assertThat(message.getField(typeUrlField))
      .isEqualTo("type.googleapis.com/coverage.Payload")
    assertThat(message.getField(valueField)).isEqualTo(payload)
    assertThat(message.getAllFields).containsKeys(typeUrlField, valueField)
  }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.Internal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InternalTest {
  @Test
  def getDefaultInstanceUsesMessageStaticAccessor(): Unit = {
    val defaultInstance: GeneratedMessageLiteSerializedFormProbe =
      Internal.getDefaultInstance(classOf[GeneratedMessageLiteSerializedFormProbe])

    assertThat(defaultInstance).isSameAs(GeneratedMessageLiteSerializedFormProbe.getDefaultInstance())
  }
}

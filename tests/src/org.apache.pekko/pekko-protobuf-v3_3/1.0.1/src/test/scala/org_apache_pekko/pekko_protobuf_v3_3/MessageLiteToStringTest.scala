/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MessageLiteToStringTest {
  @Test
  def generatedMessageLiteToStringUsesReflectivePrinter(): Unit = {
    val message: GeneratedMessageLiteSerializedFormProbe = GeneratedMessageLiteSerializedFormProbe.getDefaultInstance

    val debugString: String = message.toString

    assertThat(debugString).startsWith("# ")
    assertThat(debugString).contains("GeneratedMessageLiteSerializedFormProbe")
  }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MessageSchemaTest {
  @Test
  def generatedLiteSchemaReportsUnknownRawMessageInfoField(): Unit = {
    val encodedCountField: Array[Byte] = Array(0x08.toByte, 0x07.toByte)

    val exception: RuntimeException = assertThrows(
      classOf[RuntimeException],
      () => MessageSchemaMissingFieldMessage.parseFrom(encodedCountField)
    )
    assertThat(exception.getMessage).contains("Field missing_")
    assertThat(exception.getMessage).contains(classOf[MessageSchemaMissingFieldMessage].getName)
  }
}

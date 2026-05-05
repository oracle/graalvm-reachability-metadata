/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MessageSchemaTest {
  @Test
  def createsRawMessageSchemaFromGeneratedLiteFieldMetadata(): Unit = {
    val parsedValue: Int = MessageSchemaProbe.parseValidMessageValue()

    assertThat(parsedValue).isZero()
  }

  @Test
  def reportsKnownFieldsWhenRawMessageSchemaReferencesMissingField(): Unit = {
    val failure: RuntimeException = MessageSchemaProbe.parseInvalidMessageWithMissingField()

    assertThat(failure).hasMessageContaining("missing_")
    assertThat(failure).hasMessageContaining("Known fields")
  }
}

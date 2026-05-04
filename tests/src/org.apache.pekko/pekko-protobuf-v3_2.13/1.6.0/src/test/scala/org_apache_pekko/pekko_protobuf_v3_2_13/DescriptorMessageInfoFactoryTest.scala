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
  def generatedMessageSchemaUsesDescriptorBackedReflection(): Unit = {
    val message: DescriptorMessageInfoFactoryCoverageMessage =
      DescriptorMessageInfoFactoryCoverageMessage.getDefaultInstance

    message.parseEmptyInputThroughSchema()

    assertThat(message.getDescriptorForType.getFullName).isEqualTo("coverage.CoverageMessage")
    assertThat(message.getChildrenCount).isZero()
    assertThat(message.getChosen)
      .isSameAs(DescriptorMessageInfoFactoryCoverageNested.getDefaultInstance)
  }
}

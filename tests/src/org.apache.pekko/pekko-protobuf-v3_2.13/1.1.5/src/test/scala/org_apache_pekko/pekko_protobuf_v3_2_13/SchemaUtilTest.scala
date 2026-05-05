/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SchemaUtilTest {
  @Test
  def mapSchemaDiscoveryLoadsGeneratedDefaultEntryHolder(): Unit = {
    val message: SchemaUtilMapFieldProbe = new SchemaUtilMapFieldProbe()

    message.initializeEmptyMapSchema()

    val entriesField = message.getDescriptorForType.findFieldByName("entries")
    assertThat(entriesField.isMapField).isTrue()
    assertThat(message.getEntriesMap).isEmpty()
  }
}

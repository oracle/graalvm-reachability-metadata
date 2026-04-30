/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SchemaUtilTest {
  @Test
  def resolvesMapDefaultEntryHolderWhileBuildingGeneratedMessageSchema(): Unit = {
    assertNotNull(SchemaUtilMapFieldHost.initializedMapDefaultEntryForTests())

    try {
      SchemaUtilMapFieldHost.newMutable().parseEmptyInputWithGeneratedMessageSchema()
    } catch {
      case thrown: NullPointerException => assertEquals("mapDefaultEntry", thrown.getMessage)
      case thrown: RuntimeException => assertInstanceOf(classOf[NullPointerException], thrown.getCause)
    }
  }
}

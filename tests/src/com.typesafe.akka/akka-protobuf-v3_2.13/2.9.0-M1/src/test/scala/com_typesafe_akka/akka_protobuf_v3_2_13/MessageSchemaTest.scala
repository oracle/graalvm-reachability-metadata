/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageSchemaTest {
  @Test
  def rawMessageInfoSchemaCreationReflectsExistingField(): Unit = {
    val size: Int = ValidRawInfoLiteMessage.defaultInstance().getSerializedSize

    assertEquals(0, size)
  }

  @Test
  def rawMessageInfoSchemaCreationReportsMissingReflectedField(): Unit = {
    val error: RuntimeException = assertThrows(
      classOf[RuntimeException],
      () => BrokenRawInfoLiteMessage.defaultInstance().getSerializedSize
    )

    assertTrue(error.getMessage.contains("Field missing_"))
    assertTrue(error.getMessage.contains(classOf[BrokenRawInfoLiteMessage].getName))
  }
}

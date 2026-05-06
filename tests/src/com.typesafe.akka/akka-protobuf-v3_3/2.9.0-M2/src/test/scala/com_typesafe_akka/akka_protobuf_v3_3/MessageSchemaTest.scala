/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageSchemaTest {
  @Test
  def buildsSchemaFromRawMessageInfoFieldName(): Unit = {
    assertEquals(0, MessageSchemaTestMessages.serializedSizeForValidField())
  }

  @Test
  def reportsRawMessageInfoFieldNamesThatDoNotExistOnTheMessageClass(): Unit = {
    val exception: RuntimeException = assertThrows(
      classOf[RuntimeException],
      () => MessageSchemaTestMessages.serializedSizeForMissingField()
    )

    val message: String = exception.toString
    assertTrue(message.contains("missingField_"))
    assertTrue(message.contains("Known fields are"))
  }
}

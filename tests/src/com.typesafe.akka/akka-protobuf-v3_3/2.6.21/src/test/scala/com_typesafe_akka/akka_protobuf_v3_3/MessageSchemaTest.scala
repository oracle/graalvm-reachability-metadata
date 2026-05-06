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
  def liteRuntimeSchemaReflectsRawMessageInfoFields(): Unit = {
    val message: MessageSchemaInt32ProbeMessage = new MessageSchemaInt32ProbeMessage()

    val firstHash: Int = message.hashCode()

    assertEquals(firstHash, message.hashCode())
  }

  @Test
  def liteRuntimeSchemaReportsRawMessageInfoFieldMismatches(): Unit = {
    val message: MessageSchemaMissingFieldProbeMessage = new MessageSchemaMissingFieldProbeMessage()

    val thrown: RuntimeException = assertThrows(classOf[RuntimeException], () => message.hashCode())

    assertTrue(thrown.getMessage.contains("Field absent_"))
    assertTrue(thrown.getMessage.contains("not found"))
  }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

class MessageSchemaTest {
  @Test
  def buildsLiteRuntimeSchemaFromDeclaredMessageField(): Unit = {
    val message: MessageSchemaFieldBackedLiteMessage = MessageSchemaFieldBackedLiteMessage.getDefaultInstance()

    assertEquals(7, message.getCount)
    assertTrue(message.getSerializedSize > 0)
  }

  @Test
  def reportsMissingRawMessageInfoFieldsWithDeclaredFieldDetails(): Unit = {
    val thrown: RuntimeException = assertThrows(classOf[RuntimeException], new Executable {
      override def execute(): Unit = MessageSchemaMissingFieldLiteMessage.getDefaultInstance().getSerializedSize
    })

    assertTrue(thrown.getMessage.contains("missing_"))
    assertTrue(thrown.getMessage.contains("Known fields"))
  }
}

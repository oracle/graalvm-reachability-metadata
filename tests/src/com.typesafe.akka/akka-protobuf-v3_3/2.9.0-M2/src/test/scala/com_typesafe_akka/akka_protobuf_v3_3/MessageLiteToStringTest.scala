/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageLiteToStringTest {
  @Test
  def generatedMessageLiteToStringReflectsDeclaredAccessors(): Unit = {
    val renderedMessage: String = MessageLiteToStringTestSupport.formatSampleMessage()

    assertTrue(renderedMessage.contains("name: \"native-image\""), renderedMessage)
    assertTrue(renderedMessage.contains("tags: \"reflection\""), renderedMessage)
    assertTrue(renderedMessage.contains("counts {"), renderedMessage)
    assertTrue(renderedMessage.contains("key: \"declared_methods\""), renderedMessage)
    assertTrue(renderedMessage.contains("value: 1"), renderedMessage)
  }
}

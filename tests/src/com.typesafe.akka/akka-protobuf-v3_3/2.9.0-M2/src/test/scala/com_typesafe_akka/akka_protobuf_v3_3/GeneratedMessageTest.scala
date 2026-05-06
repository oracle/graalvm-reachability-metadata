/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import akka.protobufv3.internal.Descriptors.FieldDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedMessageTest {
  @Test
  def fieldAccessorsUseGeneratedMessageReflectionHelpers(): Unit = {
    val field: FieldDescriptor = GeneratedMessageTestSupport.fooField()
    val message: GeneratedMessageTestSupport.ReflectiveMessage = GeneratedMessageTestSupport.message("covered")

    assertTrue(message.hasField(field))
    assertEquals("covered", message.getField(field))

    val builder: GeneratedMessageTestSupport.Builder = message.toBuilder
    assertEquals("covered", builder.getField(field))

    builder.clearField(field)
    assertFalse(builder.hasField(field))

    builder.setField(field, "updated")
    assertTrue(builder.hasField(field))
    assertEquals("updated", builder.build().getField(field))
  }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13

import akka.protobufv3.internal.Struct
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SchemaUtilTest {
  @Test
  def schemaCreationForProto3MapFieldsLoadsGeneratedDefaultEntryHolder(): Unit = {
    val message: StructMapFieldProbeMessage = new StructMapFieldProbeMessage()

    message.mergeEmptyInputThroughRuntimeSchema()

    assertSame(Struct.getDescriptor, message.getDescriptorForType)
    assertEquals(0, message.getFieldsCount)
  }
}

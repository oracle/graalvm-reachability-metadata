/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.DescriptorProtos.DescriptorProto
import org.apache.pekko.protobufv3.internal.Descriptors.FieldDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedMessageV3Test {
  @Test
  def reflectiveFieldAccessorsReadGeneratedDescriptorMessages(): Unit = {
    val message: DescriptorProto = DescriptorProto.newBuilder()
      .setName("DynamicAccessHost")
      .addReservedName("first")
      .addReservedName("second")
      .build()

    val nameField: FieldDescriptor = DescriptorProto.getDescriptor.findFieldByName("name")
    val reservedNameField: FieldDescriptor = DescriptorProto.getDescriptor.findFieldByName("reserved_name")

    assertTrue(message.hasField(nameField))
    assertEquals("DynamicAccessHost", message.getField(nameField))
    assertEquals(2, message.getRepeatedFieldCount(reservedNameField))
    assertEquals("first", message.getRepeatedField(reservedNameField, 0))
    assertEquals("second", message.getRepeatedField(reservedNameField, 1))
  }
}

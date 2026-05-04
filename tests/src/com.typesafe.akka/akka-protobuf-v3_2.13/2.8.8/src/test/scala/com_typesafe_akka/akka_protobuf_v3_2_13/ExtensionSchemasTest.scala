/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ExtensionSchemasTest {
  @Test
  def fullRuntimeProto2SchemaUsesLoadedExtensionSchema(): Unit = {
    val message: Proto2FullRuntimeMessage = Proto2FullRuntimeMessage.getDefaultInstance

    message.mergeEmptyPayloadWithFullRuntimeSchema()

    assertSame(message, message.getDefaultInstanceForType)
  }
}

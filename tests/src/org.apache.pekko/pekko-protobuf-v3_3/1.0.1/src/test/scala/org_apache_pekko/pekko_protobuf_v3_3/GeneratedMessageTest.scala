/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class GeneratedMessageTest {
  @Test
  def fieldAccessorsResolveAndInvokeGeneratedMethods(): Unit = {
    val message: GeneratedMessageDynamicAccessMessage = GeneratedMessageDynamicAccessMessage.of(7)
    val builder: GeneratedMessageDynamicAccessMessage.Builder = new GeneratedMessageDynamicAccessMessage.Builder()

    builder.setField(GeneratedMessageDynamicAccessMessage.countField, 11)

    assertSame(GeneratedMessageDynamicAccessMessage.messageDescriptor, message.getDescriptorForType)
    assertEquals(7, message.getField(GeneratedMessageDynamicAccessMessage.countField))
    assertEquals(11, builder.getField(GeneratedMessageDynamicAccessMessage.countField))
    assertEquals(11, builder.build().getField(GeneratedMessageDynamicAccessMessage.countField))
  }
}

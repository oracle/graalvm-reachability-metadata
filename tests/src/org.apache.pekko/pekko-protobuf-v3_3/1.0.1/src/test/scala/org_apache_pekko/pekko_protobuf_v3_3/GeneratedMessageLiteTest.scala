/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedMessageLiteTest {
  @Test
  def buildsSchemaFromDefaultInstanceResolvedByTheLiteRuntime(): Unit = {
    val message: UnregisteredLiteMessage = UnregisteredLiteMessage.getDefaultInstance()

    assertEquals(0, message.getSerializedSize)
    assertSame(message, message.getDefaultInstanceForType())
  }

  @Test
  def rendersDeclaredAccessorsThroughLiteMessageFormatting(): Unit = {
    val text: String = UnregisteredLiteMessage.getDefaultInstance().toString

    assertTrue(text.contains("active_name: \"pekko\""))
  }

  @Test
  def resolvesPublicMessageMethodsThroughLiteRuntimeLookup(): Unit = {
    val methodName: String = GeneratedMessageLiteMethodLookup.lookupDefaultInstanceMethodName()

    assertEquals("getDefaultInstance", methodName)
  }
}

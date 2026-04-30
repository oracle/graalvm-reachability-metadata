/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.Internal
import org.apache.pekko.protobufv3.internal.StringValue
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InternalTest {
  @Test
  def resolvesDefaultMessageInstanceThroughRuntimeHelper(): Unit = {
    val defaultInstance: StringValue = Internal.getDefaultInstance(classOf[StringValue])

    assertSame(StringValue.getDefaultInstance, defaultInstance)
    assertTrue(defaultInstance.getValue.isEmpty)
  }
}

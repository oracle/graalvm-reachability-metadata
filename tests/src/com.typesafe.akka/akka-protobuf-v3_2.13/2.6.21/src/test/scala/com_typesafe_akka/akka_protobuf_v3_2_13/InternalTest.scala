/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13

import akka.protobufv3.internal.Empty
import akka.protobufv3.internal.Internal
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class InternalTest {
  @Test
  def getDefaultInstanceUsesMessageStaticAccessor(): Unit = {
    val defaultInstance: Empty = Internal.getDefaultInstance(classOf[Empty])

    assertSame(Empty.getDefaultInstance, defaultInstance)
  }
}

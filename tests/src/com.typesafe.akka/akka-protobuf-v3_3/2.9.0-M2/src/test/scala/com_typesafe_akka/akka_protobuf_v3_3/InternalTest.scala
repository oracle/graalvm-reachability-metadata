/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import akka.protobufv3.internal.Internal
import akka.protobufv3.internal.{Any => ProtobufAny}
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class InternalTest {
  @Test
  def getDefaultInstanceUsesGeneratedMessageAccessor(): Unit = {
    val defaultInstance: ProtobufAny = Internal.getDefaultInstance(classOf[ProtobufAny])

    assertSame(ProtobufAny.getDefaultInstance, defaultInstance)
  }
}

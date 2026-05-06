/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import akka.protobufv3.internal.DescriptorProtos
import akka.protobufv3.internal.Internal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class InternalTest {
  @Test
  def getDefaultInstanceResolvesGeneratedMessageDefaultAccessor(): Unit = {
    val defaultInstance: DescriptorProtos.FileDescriptorProto = Internal.getDefaultInstance(
      classOf[DescriptorProtos.FileDescriptorProto]
    )

    assertSame(DescriptorProtos.FileDescriptorProto.getDefaultInstance, defaultInstance)
    assertEquals("", defaultInstance.getName)
    assertEquals(0, defaultInstance.getDependencyCount)
  }
}

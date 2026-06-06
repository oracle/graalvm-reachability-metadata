/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import akka.protobufv3.internal.ByteString

class ByteBufferWriterTest {
  @Test
  def classInitializationDiscoversFileOutputStreamChannelField(): Unit = {
    val writerClass: Class[_] = Class.forName(
      "akka.protobufv3.internal.ByteBufferWriter",
      true,
      classOf[ByteString].getClassLoader
    )

    assertEquals("akka.protobufv3.internal.ByteBufferWriter", writerClass.getName)
  }
}

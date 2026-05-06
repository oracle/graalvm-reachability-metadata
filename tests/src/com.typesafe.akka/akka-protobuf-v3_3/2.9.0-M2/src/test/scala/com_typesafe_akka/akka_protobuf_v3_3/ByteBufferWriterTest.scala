/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import java.io.ByteArrayOutputStream
import java.lang.Void.TYPE
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType
import java.nio.ByteBuffer

import akka.protobufv3.internal.ByteString
import akka.protobufv3.internal.UnsafeByteOperations
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class ByteBufferWriterTest {
  @Test
  def writesDirectByteBufferRangeToOutputStream(): Unit = {
    val sourceBytes: Array[Byte] = Array[Byte](10, 11, 12, 13, 14, 15)
    val directBuffer: ByteBuffer = ByteBuffer.allocateDirect(sourceBytes.length)
    directBuffer.put(sourceBytes)
    directBuffer.flip()

    val byteString: ByteString = UnsafeByteOperations.unsafeWrap(directBuffer)
    val output: ByteArrayOutputStream = new ByteArrayOutputStream()

    byteStringRangeWriter.invokeWithArguments(
      byteString,
      output,
      java.lang.Integer.valueOf(1),
      java.lang.Integer.valueOf(4)
    )

    assertArrayEquals(Array[Byte](11, 12, 13, 14), output.toByteArray)
  }

  private def byteStringRangeWriter: MethodHandle = {
    val lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(
      classOf[ByteString],
      MethodHandles.lookup()
    )
    lookup.findVirtual(
      classOf[ByteString],
      "writeTo",
      methodType(
        TYPE,
        classOf[java.io.OutputStream],
        java.lang.Integer.TYPE,
        java.lang.Integer.TYPE
      )
    )
  }
}

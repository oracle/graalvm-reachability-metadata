/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType
import java.nio.ByteBuffer

import akka.protobufv3.internal.ByteString
import akka.protobufv3.internal.UnsafeByteOperations
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ByteBufferWriterTest {
  @Test
  def writesDirectNioByteStringRangeThroughInternalByteBufferWriter(): Unit = {
    val sourceBuffer: ByteBuffer = ByteBuffer.allocateDirect(8)
    sourceBuffer.put(Array[Byte](10, 20, 30, 40, 50, 60, 70, 80))
    sourceBuffer.flip()
    assertFalse(sourceBuffer.hasArray)

    val byteString: ByteString = UnsafeByteOperations.unsafeWrap(sourceBuffer)
    val byteStringClass: Class[_] = byteString.getClass
    val lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(
      byteStringClass,
      MethodHandles.lookup()
    )
    val writeToInternal: java.lang.invoke.MethodHandle = lookup.findVirtual(
      byteStringClass,
      "writeToInternal",
      methodType(java.lang.Void.TYPE, classOf[OutputStream], Integer.TYPE, Integer.TYPE)
    )

    val output: ByteArrayOutputStream = new ByteArrayOutputStream()
    writeToInternal.invokeWithArguments(byteString, output, Int.box(1), Int.box(4))

    assertArrayEquals(Array[Byte](20, 30, 40, 50), output.toByteArray)
  }
}

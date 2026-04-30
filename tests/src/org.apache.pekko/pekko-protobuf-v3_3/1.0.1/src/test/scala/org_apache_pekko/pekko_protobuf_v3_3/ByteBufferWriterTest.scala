/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import org.apache.pekko.protobufv3.internal.ByteString
import org.apache.pekko.protobufv3.internal.UnsafeByteOperations
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ByteBufferWriterTest {
  @Test
  def writesDirectByteBufferBackedByteStringRangeToOutputStream(): Unit = {
    val payload: Array[Byte] = "direct byte buffer backed protobuf data".getBytes(StandardCharsets.UTF_8)
    val directBuffer: ByteBuffer = ByteBuffer.allocateDirect(payload.length)
    directBuffer.put(payload)
    directBuffer.flip()

    val byteString: ByteString = UnsafeByteOperations.unsafeWrap(directBuffer.asReadOnlyBuffer())
    val outputStream: ByteArrayOutputStream = new ByteArrayOutputStream()

    ByteBufferWriterTest.writeRange(byteString, outputStream, 0, byteString.size())

    assertThat(outputStream.toByteArray).containsExactly(payload: _*)
  }
}

object ByteBufferWriterTest {
  private val ByteStringClass: Class[ByteString] = classOf[ByteString]
  private val Lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(
    ByteStringClass,
    MethodHandles.lookup()
  )

  private val WriteToRange: MethodHandle = Lookup.findVirtual(
    ByteStringClass,
    "writeTo",
    MethodType.methodType(
      java.lang.Void.TYPE,
      classOf[OutputStream],
      java.lang.Integer.TYPE,
      java.lang.Integer.TYPE
    )
  )

  def writeRange(byteString: ByteString, outputStream: OutputStream, offset: Int, length: Int): Unit = {
    WriteToRange.invokeWithArguments(
      byteString,
      outputStream,
      Integer.valueOf(offset),
      Integer.valueOf(length)
    )
    ()
  }
}

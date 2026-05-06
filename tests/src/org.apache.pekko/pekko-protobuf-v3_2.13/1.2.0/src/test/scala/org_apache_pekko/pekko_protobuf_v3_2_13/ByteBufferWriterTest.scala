/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8

import org.apache.pekko.protobufv3.internal.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ByteBufferWriterTest {
  @Test
  def writesDirectByteBufferToOutputStreamAndPreservesPosition(): Unit = {
    val sourceText: String = "prefix-pekkosuffix"
    val sourceBytes: Array[Byte] = sourceText.getBytes(UTF_8)
    val directBuffer: ByteBuffer = ByteBuffer.allocateDirect(sourceBytes.length)
    directBuffer.put(sourceBytes)
    directBuffer.flip()
    directBuffer.position("prefix-".length)
    directBuffer.limit("prefix-pekko".length)

    val slicedBuffer: ByteBuffer = directBuffer.slice()
    val initialPosition: Int = slicedBuffer.position()
    val output: ByteArrayOutputStream = new ByteArrayOutputStream()

    byteBufferWriterWrite.invokeWithArguments(slicedBuffer, output)

    assertThat(output.toByteArray).containsExactly(
      'p'.toByte,
      'e'.toByte,
      'k'.toByte,
      'k'.toByte,
      'o'.toByte
    )
    assertThat(slicedBuffer.position()).isEqualTo(initialPosition)
  }

  private def byteBufferWriterWrite: MethodHandle = {
    val byteBufferWriterClass: Class[_] = Class.forName(
      "org.apache.pekko.protobufv3.internal.ByteBufferWriter",
      true,
      classOf[ByteString].getClassLoader
    )
    val lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(
      byteBufferWriterClass,
      MethodHandles.lookup()
    )
    lookup.findStatic(
      byteBufferWriterClass,
      "write",
      methodType(java.lang.Void.TYPE, classOf[ByteBuffer], classOf[OutputStream])
    )
  }
}

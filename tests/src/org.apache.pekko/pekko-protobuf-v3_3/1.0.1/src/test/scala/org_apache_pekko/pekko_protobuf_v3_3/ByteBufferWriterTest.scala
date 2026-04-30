/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.ByteString
import org.apache.pekko.protobufv3.internal.UnsafeByteOperations
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

class ByteBufferWriterTest {
  @TempDir
  var tempDir: Path = _

  @Test
  def initializesFileOutputStreamChannelLookupWhenWritingDirectByteBuffers(): Unit = {
    val payload: Array[Byte] = Array[Byte](3.toByte, 1.toByte, 4.toByte, 1.toByte, 5.toByte, 9.toByte)
    val directBuffer: ByteBuffer = ByteBuffer.allocateDirect(payload.length)
    directBuffer.put(payload)
    directBuffer.flip()

    val byteString: ByteString = UnsafeByteOperations.unsafeWrap(directBuffer.slice())
    val outputFile: Path = tempDir.resolve("protobuf-direct-buffer.bin")

    val outputStream: FileOutputStream = new FileOutputStream(outputFile.toFile)
    try {
      byteString.writeTo(outputStream)
    } finally {
      outputStream.close()
    }

    val writerClass: Class[_] = Class.forName(
      "org.apache.pekko.protobufv3.internal.ByteBufferWriter",
      true,
      byteString.getClass.getClassLoader
    )

    assertEquals("org.apache.pekko.protobufv3.internal.ByteBufferWriter", writerClass.getName)
    assertArrayEquals(payload, Files.readAllBytes(outputFile))
  }
}

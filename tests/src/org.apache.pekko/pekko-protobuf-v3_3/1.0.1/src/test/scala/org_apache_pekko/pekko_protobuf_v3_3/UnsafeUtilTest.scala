/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package libcore.io {
  final class Memory {
    def peekLong(address: Long, swap: Boolean): Long = 0L

    def peekLong(address: Int, swap: Boolean): Long = 0L

    def pokeLong(address: Long, value: Long, swap: Boolean): Unit = ()

    def pokeLong(address: Int, value: Long, swap: Boolean): Unit = ()

    def pokeInt(address: Long, value: Int, swap: Boolean): Unit = ()

    def pokeInt(address: Int, value: Int, swap: Boolean): Unit = ()

    def peekInt(address: Long, swap: Boolean): Int = 0

    def peekInt(address: Int, swap: Boolean): Int = 0

    def pokeByte(address: Long, value: Byte): Unit = ()

    def pokeByte(address: Int, value: Byte): Unit = ()

    def peekByte(address: Long): Byte = 0

    def peekByte(address: Int): Byte = 0

    def pokeByteArray(address: Long, bytes: Array[Byte], offset: Int, count: Int): Unit = ()

    def pokeByteArray(address: Int, bytes: Array[Byte], offset: Int, count: Int): Unit = ()

    def peekByteArray(address: Long, bytes: Array[Byte], offset: Int, count: Int): Unit = ()

    def peekByteArray(address: Int, bytes: Array[Byte], offset: Int, count: Int): Unit = ()
  }
}

package org_apache_pekko.pekko_protobuf_v3_3 {
  import java.nio.charset.StandardCharsets

  import org.apache.pekko.protobufv3.internal.UnsafeByteOperations
  import org.assertj.core.api.Assertions.assertThat
  import org.junit.jupiter.api.Test

  class UnsafeUtilTest {
    @Test
    def validatesUtf8ThroughUnsafeAwareByteString(): Unit = {
      val text: String = "Pekko protobuf shaded runtime exercises UnsafeUtil"
      val bytes: Array[Byte] = text.getBytes(StandardCharsets.UTF_8)
      val byteString = UnsafeByteOperations.unsafeWrap(bytes)

      assertThat(byteString.isValidUtf8).isTrue()
      assertThat(byteString.toStringUtf8).isEqualTo(text)
    }
  }
}

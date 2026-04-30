/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package libcore.io {

  class Memory {
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

    def peekByte(address: Long): Byte = 0.toByte

    def peekByte(address: Int): Byte = 0.toByte

    def pokeByteArray(address: Long, values: Array[Byte], offset: Int, count: Int): Unit = ()

    def pokeByteArray(address: Int, values: Array[Byte], offset: Int, count: Int): Unit = ()

    def peekByteArray(address: Long, values: Array[Byte], offset: Int, count: Int): Unit = ()

    def peekByteArray(address: Int, values: Array[Byte], offset: Int, count: Int): Unit = ()
  }
}

package org_apache_pekko.pekko_protobuf_v3_3 {

  import org.apache.pekko.protobufv3.internal.CodedOutputStream
  import org.junit.jupiter.api.Assertions.assertArrayEquals
  import org.junit.jupiter.api.Assertions.assertEquals
  import org.junit.jupiter.api.Test

  class UnsafeUtilTest {
    @Test
    def writesLengthDelimitedPayloadThroughCodedOutputStream(): Unit = {
      val buffer: Array[Byte] = Array.fill[Byte](5)(0.toByte)
      val payload: Array[Byte] = Array[Byte](1.toByte, 2.toByte, 3.toByte, 4.toByte)
      val output: CodedOutputStream = CodedOutputStream.newInstance(buffer)

      output.writeByteArrayNoTag(payload)
      output.flush()

      assertEquals(0, output.spaceLeft())
      assertArrayEquals(Array[Byte](4.toByte, 1.toByte, 2.toByte, 3.toByte, 4.toByte), buffer)
    }
  }
}

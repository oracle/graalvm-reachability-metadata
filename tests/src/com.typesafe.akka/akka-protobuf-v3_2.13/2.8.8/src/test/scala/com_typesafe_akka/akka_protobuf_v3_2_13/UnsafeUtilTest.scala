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

    def pokeByteArray(address: Long, bytes: Array[Byte], offset: Int, count: Int): Unit = ()

    def pokeByteArray(address: Int, bytes: Array[Byte], offset: Int, count: Int): Unit = ()

    def peekByteArray(address: Long, bytes: Array[Byte], offset: Int, count: Int): Unit = ()

    def peekByteArray(address: Int, bytes: Array[Byte], offset: Int, count: Int): Unit = ()
  }
}

package com_typesafe_akka.akka_protobuf_v3_2_13 {
  import java.lang.invoke.MethodHandles
  import java.lang.invoke.MethodType.methodType

  import org.junit.jupiter.api.Assertions.assertEquals

  import akka.protobufv3.internal.ByteString
  import org.junit.jupiter.api.Test

  class UnsafeUtilTest {
    @Test
    def allocateInstanceInitializesUnsafeUtilAndBypassesConstructors(): Unit = {
      assertEquals("libcore.io.Memory", classOf[libcore.io.Memory].getName)

      val unsafeUtilClass: Class[_] = Class.forName(
        "akka.protobufv3.internal.UnsafeUtil",
        true,
        classOf[ByteString].getClassLoader
      )
      val lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(
        unsafeUtilClass,
        MethodHandles.lookup()
      )
      val allocateInstance = lookup.findStatic(
        unsafeUtilClass,
        "allocateInstance",
        methodType(classOf[Object], classOf[Class[_]])
      )

      val instance = allocateInstance.invokeWithArguments(classOf[ConstructorGuardedType]).asInstanceOf[ConstructorGuardedType]

      assertEquals(0, instance.constructorValue)
    }
  }

  final class ConstructorGuardedType private () {
    private val initializedValue: Int = 42

    throw new AssertionError("Unsafe allocation must not invoke constructors")

    def constructorValue: Int = initializedValue
  }
}

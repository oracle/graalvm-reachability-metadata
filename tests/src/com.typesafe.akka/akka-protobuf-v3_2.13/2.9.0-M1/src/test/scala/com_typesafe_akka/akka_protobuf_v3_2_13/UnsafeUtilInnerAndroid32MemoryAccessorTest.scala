/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13

import java.lang.Void
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType
import java.lang.reflect.Field

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

import sun.misc.Unsafe

class UnsafeUtilInnerAndroid32MemoryAccessorTest {
  @Test
  def getStaticObjectReadsStaticFieldThroughAndroid32Accessor(): Unit = {
    val accessorClass: Class[_] = Class.forName("akka.protobufv3.internal.UnsafeUtil$Android32MemoryAccessor")
    val lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(
      accessorClass,
      MethodHandles.lookup()
    )
    val constructor: MethodHandle = lookup.findConstructor(
      accessorClass,
      methodType(Void.TYPE, classOf[Unsafe])
    )
    val accessor: Object = constructor.invokeWithArguments(null.asInstanceOf[Unsafe])
    val getStaticObject: MethodHandle = lookup.findVirtual(
      accessorClass,
      "getStaticObject",
      methodType(classOf[Object], classOf[Field])
    )
    val field: Field = classOf[java.lang.Boolean].getField("TRUE")

    val value: Object = getStaticObject.invokeWithArguments(accessor, field)

    assertSame(java.lang.Boolean.TRUE, value)
  }
}

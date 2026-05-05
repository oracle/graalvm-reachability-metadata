/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import java.lang.Void.TYPE
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType

import org.apache.pekko.protobufv3.internal.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UnsafeUtilInnerAndroid32MemoryAccessorTest {
  @Test
  def getStaticObjectReadsStaticFieldValue(): Unit = {
    val accessorClass: Class[_] = Class.forName(
      "org.apache.pekko.protobufv3.internal.UnsafeUtil$Android32MemoryAccessor",
      true,
      classOf[ByteString].getClassLoader
    )
    val lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(
      accessorClass,
      MethodHandles.lookup()
    )
    val constructor: MethodHandle = lookup.findConstructor(
      accessorClass,
      methodType(TYPE, classOf[sun.misc.Unsafe])
    )
    val accessor: Object = constructor
      .invokeWithArguments(null.asInstanceOf[Object])
      .asInstanceOf[Object]
    val getStaticObject: MethodHandle = lookup.findVirtual(
      accessorClass,
      "getStaticObject",
      methodType(classOf[Object], classOf[java.lang.reflect.Field])
    )

    val emptyField: java.lang.reflect.Field = classOf[ByteString].getField("EMPTY")
    val value: Object = getStaticObject
      .invokeWithArguments(accessor, emptyField)
      .asInstanceOf[Object]

    assertThat(value).isSameAs(ByteString.EMPTY)
  }
}

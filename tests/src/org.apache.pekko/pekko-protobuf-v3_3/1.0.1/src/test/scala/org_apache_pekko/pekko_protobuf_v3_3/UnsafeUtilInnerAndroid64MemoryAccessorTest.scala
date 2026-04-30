/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class UnsafeUtilInnerAndroid64MemoryAccessorTest {
  @Test
  def readsStaticFieldThroughAndroid64Accessor(): Unit = {
    val accessorClass: Class[?] = Class.forName(
      "org.apache.pekko.protobufv3.internal.UnsafeUtil$Android64MemoryAccessor"
    )
    val constructor: Constructor[?] = accessorClass.getDeclaredConstructor(classOf[sun.misc.Unsafe])
    constructor.setAccessible(true)
    val accessor: Object = constructor.newInstance(null.asInstanceOf[sun.misc.Unsafe])
    val getStaticObject: Method = accessorClass.getDeclaredMethod("getStaticObject", classOf[Field])
    getStaticObject.setAccessible(true)
    val systemOut: Field = classOf[System].getField("out")

    assertSame(System.out, getStaticObject.invoke(accessor, systemOut))
  }
}

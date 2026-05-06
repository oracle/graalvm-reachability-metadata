/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType
import java.lang.reflect.Field

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class UnsafeUtilInnerAndroid32MemoryAccessorTest {
  @Test
  def getStaticObjectReadsGeneratedMapDefaultEntryField(): Unit = {
    val accessorClass: Class[_] = Class.forName(
      "akka.protobufv3.internal.UnsafeUtil$Android32MemoryAccessor"
    )
    val lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(
      accessorClass,
      MethodHandles.lookup()
    )
    val constructor: MethodHandle = lookup.findConstructor(
      accessorClass,
      methodType(java.lang.Void.TYPE, classOf[sun.misc.Unsafe])
    )
    val getStaticObject: MethodHandle = lookup.findVirtual(
      accessorClass,
      "getStaticObject",
      methodType(classOf[Object], classOf[Field])
    )
    val unsafe: sun.misc.Unsafe = null
    val accessor: Object = constructor.invokeWithArguments(unsafe).asInstanceOf[Object]
    val defaultEntryField: Field = classOf[StructMapFieldProbeMessage.FieldsDefaultEntryHolder]
      .getField("defaultEntry")

    val actualDefaultEntry: Object = getStaticObject
      .invokeWithArguments(accessor, defaultEntryField)
      .asInstanceOf[Object]

    assertSame(
      StructMapFieldProbeMessage.FieldsDefaultEntryHolder.defaultEntry,
      actualDefaultEntry
    )
  }
}

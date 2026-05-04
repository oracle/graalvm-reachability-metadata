/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UnsafeUtilInnerAndroid32MemoryAccessorTest {
  @Test
  def android32AccessorReadsStaticMapDefaultEntry(): Unit = {
    val accessorClass: Class[_] = Class.forName(
      "org.apache.pekko.protobufv3.internal.UnsafeUtil$Android32MemoryAccessor")
    val constructor: Constructor[_] = accessorClass.getDeclaredConstructor(
      classOf[sun.misc.Unsafe])
    constructor.setAccessible(true)
    val accessor: AnyRef = constructor
      .newInstance(null.asInstanceOf[sun.misc.Unsafe])
      .asInstanceOf[AnyRef]
    val getStaticObject: Method = accessorClass.getDeclaredMethod(
      "getStaticObject",
      classOf[Field])
    getStaticObject.setAccessible(true)

    val holderClass: Class[_] = classOf[SchemaUtilMapFieldProbe.EntriesDefaultEntryHolder]
    val defaultEntryField: Field = holderClass.getField("defaultEntry")
    val defaultEntry: AnyRef = SchemaUtilMapFieldProbe.EntriesDefaultEntryHolder.defaultEntry

    assertThat(getStaticObject.invoke(accessor, defaultEntryField)).isSameAs(defaultEntry)
  }
}

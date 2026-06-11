/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beachape.enumeratum_macros_3

import enumeratum.NoSuchMember
import enumeratum.ValueEnumMacros
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.lang.reflect.Field
import java.lang.reflect.Method

class ValueEnumMacrosTest {
  @Test
  def mapsSingletonModuleFieldsToValueOfInstances(): Unit = {
    val moduleField: Field = NoSuchMember.getClass.getField("MODULE$")
    val mapper: Method = ValueEnumMacros.getClass.getDeclaredMethod(
      "unapply$$anonfun$1",
      classOf[Field]
    )
    mapper.setAccessible(true)

    val result = mapper.invoke(null, moduleField).asInstanceOf[NoSuchMember.type]

    assertThat(result).isSameAs(NoSuchMember)
  }
}

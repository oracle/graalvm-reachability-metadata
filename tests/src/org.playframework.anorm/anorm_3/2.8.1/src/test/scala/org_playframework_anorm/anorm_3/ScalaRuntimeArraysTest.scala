/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ScalaRuntimeArraysTest {
  @Test
  def createsMultidimensionalReferenceArray(): Unit = {
    val values: Array[Array[String]] = ScalaRuntimeArraysAccess.newStringMatrix(2, 3)

    assertEquals(2, values.length)
    assertEquals(3, values(0).length)
    assertEquals(3, values(1).length)
    assertNull(values(0)(0))

    values(1)(2) = "anorm"
    assertEquals("anorm", values(1)(2))
  }

  @Test
  def createsMultidimensionalPrimitiveArray(): Unit = {
    val values: Array[Array[Int]] = ScalaRuntimeArraysAccess.newIntMatrix(2, 2)

    assertEquals(2, values.length)
    assertEquals(2, values(0).length)
    assertEquals(0, values(0)(0))

    values(1)(1) = 28
    assertEquals(28, values(1)(1))
  }
}

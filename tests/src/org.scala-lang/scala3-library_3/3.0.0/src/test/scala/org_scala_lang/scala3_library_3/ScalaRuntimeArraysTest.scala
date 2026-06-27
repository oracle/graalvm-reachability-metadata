/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala3_library_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

final class ScalaRuntimeArraysTest {
  @Test
  def newArrayCreatesMultidimensionalReferenceArray(): Unit = {
    val dimensions: Array[Int] = Array(2, 3)

    val array: Array[Array[String]] = ScalaRuntimeArraysInvoker.newStringMatrix(dimensions)

    assertThat(array.length).isEqualTo(2)
    assertThat(array(0).length).isEqualTo(3)
    assertThat(array(1).length).isEqualTo(3)
    array(0)(1) = "scala"
    assertThat(array(0)(1)).isEqualTo("scala")
    assertThat(array.getClass.getComponentType).isEqualTo(classOf[Array[String]])
  }
}

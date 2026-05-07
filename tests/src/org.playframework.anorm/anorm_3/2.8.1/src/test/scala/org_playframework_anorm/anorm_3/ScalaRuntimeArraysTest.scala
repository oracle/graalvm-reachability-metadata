/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScalaRuntimeArraysTest {
  @Test
  def createsMultidimensionalArrayWithRuntimeHelper(): Unit = {
    val cells: Array[Array[String]] = ScalaRuntimeArraysHelper.newStringMatrix(2, 3)
    cells(0)(1) = "anorm"
    cells(1)(2) = "scala"

    assertThat(cells.length).isEqualTo(2)
    assertThat(cells(0).length).isEqualTo(3)
    assertThat(cells(0)(1)).isEqualTo("anorm")
    assertThat(cells(1)(2)).isEqualTo("scala")
  }
}

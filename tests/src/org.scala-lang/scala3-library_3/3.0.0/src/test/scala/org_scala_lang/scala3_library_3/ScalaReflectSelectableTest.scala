/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala3_library_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.reflect.Selectable

final class ScalaReflectSelectableTest {
  @Test
  def selectDynamicReadsPublicField(): Unit = {
    val selectable: Selectable = Selectable.reflectiveSelectable(java.lang.Integer.valueOf(0))

    val maximum: Any = selectable.selectDynamic("MAX_VALUE")

    assertThat(maximum).isEqualTo(java.lang.Integer.valueOf(Int.MaxValue))
  }

  @Test
  def applyDynamicInvokesPublicMethod(): Unit = {
    val selectable: Selectable = Selectable.reflectiveSelectable("metadata")

    val substring: Any = selectable.applyDynamic(
      "substring",
      java.lang.Integer.TYPE,
      java.lang.Integer.TYPE
    )(1, 5)

    assertThat(substring).isEqualTo("etad")
  }
}

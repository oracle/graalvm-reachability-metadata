/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.reflect.Selectable

class ScalaReflectSelectableTest {
  @Test
  def selectDynamicReadsPublicStaticFields(): Unit = {
    val selectable: Selectable = Selectable.reflectiveSelectable(Integer.valueOf(0))

    val selected: Any = selectable.selectDynamic("MAX_VALUE")

    assertEquals(Integer.valueOf(Int.MaxValue), selected)
  }

  @Test
  def applyDynamicInvokesPublicMethodsWithArguments(): Unit = {
    val selectable: Selectable = Selectable.reflectiveSelectable("native-image")

    val selected: Any = selectable.applyDynamic(
      "substring",
      classOf[Int],
      classOf[Int]
    )(Integer.valueOf(0), Integer.valueOf(6))

    assertEquals("native", selected)
  }
}

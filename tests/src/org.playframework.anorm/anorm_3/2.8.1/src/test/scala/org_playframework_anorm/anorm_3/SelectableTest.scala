/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import java.time.Month

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.reflect.Selectable

class SelectableTest {
  @Test
  def selectsPublicFieldDynamically(): Unit = {
    val selectable: Selectable = Selectable.reflectiveSelectable(Month.JANUARY)

    val selected: Any = selectable.selectDynamic("DECEMBER")

    assertThat(selected.asInstanceOf[Object]).isEqualTo(Month.DECEMBER)
  }

  @Test
  def invokesPublicMethodDynamically(): Unit = {
    val selectable: Selectable = Selectable.reflectiveSelectable("playframework")
    val selected: Any = selectable.applyDynamic("substring", classOf[Int], classOf[Int])(
      Integer.valueOf(0),
      Integer.valueOf(4)
    )

    assertThat(selected.asInstanceOf[Object]).isEqualTo("play")
  }
}

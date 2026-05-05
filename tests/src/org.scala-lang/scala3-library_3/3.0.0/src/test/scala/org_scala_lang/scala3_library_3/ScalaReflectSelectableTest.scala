/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala3_library_3

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.reflect.Selectable.reflectiveSelectable
import scala.util.NotGiven

class ScalaReflectSelectableTest {
  @Test
  def selectsPublicFieldAndInvokesPublicMethod(): Unit = {
    val selectable = reflectiveSelectable(NotGiven)

    assertSame(NotGiven, selectable.selectDynamic("MODULE$"))
    assertTrue(selectable.applyDynamic("value")().isInstanceOf[NotGiven[?]])
  }
}

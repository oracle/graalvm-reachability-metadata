/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.runtime.ScalaRunTime

final class ScalaRunTimeTest {
  @Test
  def createsArrayClassForRuntimeElementClass(): Unit = {
    val arrayClass: Class[_] = ScalaRunTime.arrayClass(classOf[String])

    assertThat(arrayClass).isEqualTo(classOf[Array[String]])
  }

  @Test
  def rendersScalaCollectionsWithRuntimeStringFormatting(): Unit = {
    val rendered: String = ScalaRunTime.stringOf(List("alpha", "beta"))

    assertThat(rendered).isEqualTo("List(alpha, beta)")
  }
}

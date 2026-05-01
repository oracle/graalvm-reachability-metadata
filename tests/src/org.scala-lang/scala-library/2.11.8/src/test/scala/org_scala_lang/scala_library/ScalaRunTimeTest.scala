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
import scala.xml.Elem
import scala.xml.Null
import scala.xml.Text
import scala.xml.TopScope

final class ScalaRunTimeTest {
  @Test
  def createsArrayClassForRuntimeElementClass(): Unit = {
    val arrayClass: Class[_] = ScalaRunTime.arrayClass(classOf[String])

    assertThat(arrayClass).isEqualTo(classOf[Array[String]])
  }

  @Test
  def rendersXmlNodesWithTheirOwnStringFormatting(): Unit = {
    val node: Elem = Elem(null, "entry", Null, TopScope, false, Text("value"))

    val rendered: String = ScalaRunTime.stringOf(node)

    assertThat(rendered).isEqualTo("<entry>value</entry>")
  }
}

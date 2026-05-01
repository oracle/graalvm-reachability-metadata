/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.reflect.ClassTag

final class ClassTagTest {
  @Test
  def createsReferenceArrayWithCustomClassTag(): Unit = {
    val stringTag: ClassTag[String] = new RuntimeClassTag[String](classOf[String])

    val strings: Array[String] = stringTag.newArray(3)

    assertThat(strings.length).isEqualTo(3)
    assertThat(strings.getClass).isEqualTo(classOf[Array[String]])
    strings(0) = "scala"
    assertThat(strings(0)).isEqualTo("scala")
  }

  private final class RuntimeClassTag[T](override val runtimeClass: Class[_]) extends ClassTag[T]
}

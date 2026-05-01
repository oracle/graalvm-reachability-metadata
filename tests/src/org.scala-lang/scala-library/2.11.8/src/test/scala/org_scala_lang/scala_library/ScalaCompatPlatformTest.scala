/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.compat.Platform

final class ScalaCompatPlatformTest {
  @Test
  def resolvesLoadedClassesThroughPlatformApi(): Unit = {
    val loadedClass: Class[_] = Platform.getClassForName("java.lang.String")

    assertThat(loadedClass).isSameAs(classOf[String])
  }

  @Test
  def createsReferenceArraysThroughPlatformApi(): Unit = {
    val createdArray: Array[String] = Platform.createArray(classOf[String], 3).asInstanceOf[Array[String]]

    assertThat(createdArray.length).isEqualTo(3)
    assertThat(createdArray(0)).isNull()
  }
}

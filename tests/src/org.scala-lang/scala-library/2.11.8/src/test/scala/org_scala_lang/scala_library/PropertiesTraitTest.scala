/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.util.Properties

final class PropertiesTraitTest {
  @Test
  def readsScalaPropertiesThroughPublicPropertiesApi(): Unit = {
    val propertyName: String = "graalvm.reachability.metadata.coverage"
    val fallback: String = "fallback-from-test"

    withSystemProperty(s"scala.$propertyName", null) {
      val value: String = Properties.scalaPropOrElse(propertyName, fallback)

      assertThat(value).isEqualTo(fallback)
    }
  }

  @Test
  def evaluatesJavaSpecificationVersionFromSystemProperties(): Unit = {
    withSystemProperty("java.specification.version", "1.8") {
      val atLeastJava17: Boolean = Properties.isJavaAtLeast("1.7")

      assertThat(atLeastJava17).isTrue()
    }
  }

  private def withSystemProperty[A](name: String, value: String)(body: => A): A = {
    val previous: String = System.getProperty(name)
    try {
      if (value == null) {
        System.clearProperty(name)
      } else {
        System.setProperty(name, value)
      }
      body
    } finally {
      if (previous == null) {
        System.clearProperty(name)
      } else {
        System.setProperty(name, previous)
      }
    }
  }
}

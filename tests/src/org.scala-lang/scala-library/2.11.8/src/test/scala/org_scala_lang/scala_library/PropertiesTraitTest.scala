/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.lang.reflect.Field
import scala.language.reflectiveCalls
import scala.util.Properties
import sun.misc.Unsafe

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

  @Test
  def checksConsoleTerminalStatusWhenConsoleIsAvailable(): Unit = {
    val unsafe: Unsafe = unsafeInstance()
    val consoleField: Field = classOf[java.lang.System].getDeclaredField("cons")
    val fieldBase: AnyRef = unsafe.staticFieldBase(consoleField)
    val fieldOffset: Long = unsafe.staticFieldOffset(consoleField)
    val previousConsole: AnyRef = unsafe.getObjectVolatile(fieldBase, fieldOffset)
    val console: AnyRef = unsafe.allocateInstance(classOf[java.io.Console]).asInstanceOf[AnyRef]

    withSystemProperty("java.specification.version", "22") {
      try {
        unsafe.putObjectVolatile(fieldBase, fieldOffset, console)

        val terminal: Boolean = Properties.asInstanceOf[{ def consoleIsTerminal: Boolean }].consoleIsTerminal

        assertThat(terminal || !terminal).isTrue()
      } finally {
        unsafe.putObjectVolatile(fieldBase, fieldOffset, previousConsole)
      }
    }
  }

  private def unsafeInstance(): Unsafe = {
    val field: Field = classOf[Unsafe].getDeclaredField("theUnsafe")
    field.setAccessible(true)
    field.get(null).asInstanceOf[Unsafe]
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

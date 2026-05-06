/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import java.util.Properties

import scala.beans.BeanProperty

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import slick.util.BeanConfigurator

class BeanConfiguratorTarget {
  @BeanProperty var intValue: Int = 0
  @BeanProperty var longValue: Long = 0L
  @BeanProperty var booleanValue: Boolean = false
  @BeanProperty var stringValue: String = ""
  @BeanProperty var objectValue: AnyRef = _
}

class BeanConfiguratorTest {
  @Test
  def configuresJavaBeanPropertiesWithSupportedTypes(): Unit = {
    val target = new BeanConfiguratorTarget
    val objectValue = new Object
    val properties = new Properties
    properties.setProperty("intValue", "42")
    properties.setProperty("longValue", "9223372036854775806")
    properties.setProperty("booleanValue", "true")
    properties.setProperty("stringValue", "configured")
    properties.put("objectValue", objectValue)

    BeanConfigurator.configure(target, properties)

    assertThat(target.intValue).isEqualTo(42)
    assertThat(target.longValue).isEqualTo(9223372036854775806L)
    assertThat(target.booleanValue).isTrue
    assertThat(target.stringValue).isEqualTo("configured")
    assertThat(target.objectValue).isSameAs(objectValue)
  }
}

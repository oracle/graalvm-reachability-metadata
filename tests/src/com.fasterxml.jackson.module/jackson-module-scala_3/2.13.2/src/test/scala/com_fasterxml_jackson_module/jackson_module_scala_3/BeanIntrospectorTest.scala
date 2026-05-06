/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_3

import com.fasterxml.jackson.module.scala.introspect.BeanDescriptor
import com.fasterxml.jackson.module.scala.introspect.BeanIntrospector
import com.fasterxml.jackson.module.scala.introspect.PropertyDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BeanIntrospectorTest {
  @Test
  def introspectsConstructorParametersAndDefaultValues(): Unit = {
    val descriptor: BeanDescriptor = BeanIntrospector(classOf[DefaultedConstructorBean])
    val properties: Map[String, PropertyDescriptor] = propertiesByName(descriptor)

    assertEquals(classOf[DefaultedConstructorBean], descriptor.beanType)
    assertTrue(properties.contains("name"))
    assertTrue(properties.contains("count"))

    val nameProperty: PropertyDescriptor = properties("name")
    assertTrue(nameProperty.param.isDefined)
    assertEquals(0, nameProperty.param.get.index)
    assertFalse(nameProperty.param.get.defaultValue.isDefined)
    assertTrue(nameProperty.field.isDefined)
    assertTrue(nameProperty.getter.isDefined)

    val countProperty: PropertyDescriptor = properties("count")
    assertTrue(countProperty.param.isDefined)
    assertEquals(1, countProperty.param.get.index)
    assertTrue(countProperty.param.get.defaultValue.isDefined)
    assertEquals(Integer.valueOf(7), countProperty.param.get.defaultValue.get())
  }

  @Test
  def introspectsDeclaredFieldsMethodsAndLazyValues(): Unit = {
    val descriptor: BeanDescriptor = BeanIntrospector(classOf[MethodBackedLazyBean])
    val properties: Map[String, PropertyDescriptor] = propertiesByName(descriptor)

    assertEquals(classOf[MethodBackedLazyBean], descriptor.beanType)
    assertTrue(properties.contains("stored"))
    assertTrue(properties.contains("computed"))

    val storedProperty: PropertyDescriptor = properties("stored")
    assertTrue(storedProperty.field.isDefined)

    val computedProperty: PropertyDescriptor = properties("computed")
    assertTrue(computedProperty.field.isEmpty)
    assertTrue(computedProperty.getter.isDefined)
    assertTrue(computedProperty.setter.isDefined)
  }

  private def propertiesByName(descriptor: BeanDescriptor): Map[String, PropertyDescriptor] = {
    descriptor.properties.map(property => property.name -> property).toMap
  }
}

case class DefaultedConstructorBean(name: String, count: Int = 7)

class MethodBackedLazyBean(private var stored: String) {
  lazy val cached: String = stored.reverse

  def computed: String = stored.toUpperCase

  def computed_=(value: String): Unit = {
    stored = value.toLowerCase
  }
}

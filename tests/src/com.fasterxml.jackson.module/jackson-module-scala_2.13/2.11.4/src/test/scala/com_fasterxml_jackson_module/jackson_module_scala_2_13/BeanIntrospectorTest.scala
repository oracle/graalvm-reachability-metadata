/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.module.scala.introspect.{BeanDescriptor, BeanIntrospector, PropertyDescriptor}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test

class BeanIntrospectorTest {
  @Test
  def introspectsScalaConstructorFieldsAndProperties(): Unit = {
    val descriptor: BeanDescriptor = BeanIntrospector(classOf[BeanIntrospectorFixtureBean])
    val propertiesByName: Map[String, PropertyDescriptor] = descriptor.properties.map { property =>
      property.name -> property
    }.toMap

    assertEquals(classOf[BeanIntrospectorFixtureBean], descriptor.beanType)
    assertTrue(propertiesByName.contains("name"))
    assertTrue(propertiesByName.contains("count"))
    assertTrue(propertiesByName.contains("mutable"))
    assertTrue(propertiesByName.contains("code"))
    assertTrue(propertiesByName.contains("inherited"))

    val nameProperty: PropertyDescriptor = propertiesByName("name")
    assertTrue(nameProperty.param.isDefined)
    assertTrue(nameProperty.param.exists(_.defaultValue.isDefined))
    val defaultName: AnyRef = nameProperty.param.flatMap(_.defaultValue).map(defaultValue => defaultValue()).orNull
    assertEquals("default-name", defaultName)
    assertTrue(nameProperty.beanGetter.isDefined)
    assertTrue(nameProperty.beanSetter.isDefined)

    val countProperty: PropertyDescriptor = propertiesByName("count")
    assertTrue(countProperty.param.isDefined)
    assertTrue(countProperty.setter.isDefined)

    val mutableProperty: PropertyDescriptor = propertiesByName("mutable")
    assertTrue(mutableProperty.field.isDefined)
    assertTrue(mutableProperty.getter.isDefined)
    assertTrue(mutableProperty.setter.isDefined)

    val codeProperty: PropertyDescriptor = propertiesByName("code")
    assertTrue(codeProperty.field.isEmpty)
    assertTrue(codeProperty.getter.isDefined)
    assertTrue(codeProperty.setter.isDefined)
  }
}

class BeanIntrospectorFixtureBase(val inherited: String) {
  private var _computed: Int = 1

  def computed: Int = _computed

  def computed_=(value: Int): Unit = {
    _computed = value
  }
}

class BeanIntrospectorFixtureBean(val name: String = "default-name", var count: Int = 7)
    extends BeanIntrospectorFixtureBase("base-value") {
  var mutable: String = "initial"
  private var _code: String = "alpha"

  def code: String = _code

  def code_=(value: String): Unit = {
    _code = value
  }

  def getName: String = name

  def setName(value: String): Unit = ()
}

object BeanIntrospectorFixtureBean

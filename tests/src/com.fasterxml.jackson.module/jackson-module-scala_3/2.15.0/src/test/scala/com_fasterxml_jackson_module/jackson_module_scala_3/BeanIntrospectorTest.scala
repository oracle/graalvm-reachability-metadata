/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_3

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.introspect.BeanDescriptor
import com.fasterxml.jackson.module.scala.introspect.BeanIntrospector
import com.fasterxml.jackson.module.scala.introspect.PropertyDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BeanIntrospectorTest {
  private val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  @Test
  def appliesScalaConstructorDefaultsDuringDeserialization(): Unit = {
    val value: BeanIntrospectorDefaultedValue =
      mapper.readValue("""{"name":"sample"}""", classOf[BeanIntrospectorDefaultedValue])

    assertThat(value).isEqualTo(BeanIntrospectorDefaultedValue("sample", 7))
  }

  @Test
  def exposesConstructorDefaultMetadataForCaseClassParameters(): Unit = {
    val descriptor: BeanDescriptor = BeanIntrospector(classOf[BeanIntrospectorDefaultedValue])
    val countProperty: Option[PropertyDescriptor] = descriptor.properties.find(_.name == "count")

    assertThat(countProperty.isDefined).isTrue()
    assertThat(countProperty.get.param.isDefined).isTrue()

    val defaultSupplier: Option[() => AnyRef] = countProperty.get.param.flatMap(_.defaultValue)
    assertThat(defaultSupplier.isDefined).isTrue()
    assertThat(defaultSupplier.get.apply()).isEqualTo(7)
  }

  @Test
  def discoversScalaGetterSetterAndLazyValProperties(): Unit = {
    val descriptor: BeanDescriptor = BeanIntrospector(classOf[BeanIntrospectorAccessorBean])
    val propertyNames: Set[String] = descriptor.properties.map(_.name).toSet

    assertThat(propertyNames.contains("nickname")).isTrue()
    assertThat(propertyNames.contains("computedNickname")).isTrue()
  }

  @Test
  def bindsScalaSetterDiscoveredByTheRegisteredModule(): Unit = {
    val bean: BeanIntrospectorAccessorBean =
      mapper.readValue("""{"nickname":"Ada"}""", classOf[BeanIntrospectorAccessorBean])

    assertThat(bean.nickname).isEqualTo("Ada")
    assertThat(mapper.writeValueAsString(bean)).contains("\"nickname\":\"Ada\"")
  }
}

final case class BeanIntrospectorDefaultedValue(name: String, count: Int = 7)

class BeanIntrospectorAccessorBean() {
  private var storedNickname: String = "initial"

  lazy val computedNickname: String = s"$storedNickname-computed"

  def nickname: String = storedNickname

  def nickname_=(value: String): Unit = {
    storedNickname = value
  }
}

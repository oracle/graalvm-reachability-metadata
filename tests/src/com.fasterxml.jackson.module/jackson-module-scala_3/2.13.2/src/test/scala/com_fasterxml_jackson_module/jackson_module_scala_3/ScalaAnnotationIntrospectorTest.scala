/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_3

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.introspect.ScalaAnnotationIntrospector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

class ScalaAnnotationIntrospectorTest {
  @Test
  def recognizesScalaCaseClassConstructorAsCreator(): Unit = {
    val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
    val constructors: Iterable[AnnotatedConstructor] = mapper.getDeserializationConfig
      .introspectForCreation(mapper.constructType(classOf[CreatorIntrospectionBean]))
      .getConstructors
      .asScala

    assertTrue(constructors.exists(constructor => ScalaAnnotationIntrospector.hasCreatorAnnotation(constructor)))
  }

  @Test
  def deserializesCaseClassWithCreatorDiscoveredByScalaIntrospector(): Unit = {
    val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

    val value: CreatorIntrospectionBean = mapper.readValue(
      """{"name":"Ada","count":2}""",
      classOf[CreatorIntrospectionBean]
    )

    assertEquals(CreatorIntrospectionBean("Ada", 2), value)
  }
}

case class CreatorIntrospectionBean(name: String, count: Int)

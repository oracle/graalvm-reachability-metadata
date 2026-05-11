/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScalaAnnotationIntrospectorTest {
  @Test
  def deserializesScalaBeanUsingImplicitCreatorConstructor(): Unit = {
    val mapper: ObjectMapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    val value: ScalaAnnotationIntrospectorFixture = mapper.readValue(
      """{"name":"scala","count":13}""",
      classOf[ScalaAnnotationIntrospectorFixture]
    )

    assertEquals(ScalaAnnotationIntrospectorFixture("scala", 13), value)
  }
}

case class ScalaAnnotationIntrospectorFixture(name: String, count: Int)

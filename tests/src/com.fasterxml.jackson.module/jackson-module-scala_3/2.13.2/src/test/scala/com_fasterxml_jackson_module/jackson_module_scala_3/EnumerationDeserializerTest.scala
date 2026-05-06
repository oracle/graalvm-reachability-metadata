/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_3

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnumerationDeserializerTest {
  @Test
  def deserializesLegacyEnumerationValueObject(): Unit = {
    val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
    val json: String =
      """{"enumClass":"com_fasterxml_jackson_module.jackson_module_scala_3.LegacyTrafficLight","value":"Green"}"""

    val value: Enumeration#Value = mapper.readValue(json, classOf[Enumeration#Value])

    assertEquals(LegacyTrafficLight.Green, value)
  }
}

object LegacyTrafficLight extends Enumeration {
  val Red: Value = Value("Red")
  val Green: Value = Value("Green")
  val Yellow: Value = Value("Yellow")
}

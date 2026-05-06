/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_3

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnumerationSerializerTest {
  @Test
  def serializesLegacyEnumerationValueObject(): Unit = {
    val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

    val json: String = mapper.writeValueAsString(SerializerTrafficLight.Green)
    val root: JsonNode = mapper.readTree(json)

    assertEquals(
      "com_fasterxml_jackson_module.jackson_module_scala_3.SerializerTrafficLight",
      root.get("enumClass").asText()
    )
    assertEquals("Green", root.get("value").asText())
  }
}

object SerializerTrafficLight extends Enumeration {
  val Red: Value = Value("Red")
  val Green: Value = Value("Green")
  val Yellow: Value = Value("Yellow")
}

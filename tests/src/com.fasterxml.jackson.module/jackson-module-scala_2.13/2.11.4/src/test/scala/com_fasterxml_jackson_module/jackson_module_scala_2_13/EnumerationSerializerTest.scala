/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnumerationSerializerTest {
  @Test
  def serializesLegacyScalaEnumerationObjectShape(): Unit = {
    val mapper: ObjectMapper with ScalaObjectMapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    val json: String = mapper.writeValueAsString(EnumerationSerializerFixture.Running)
    val tree: JsonNode = mapper.readTree(json)

    assertEquals(EnumerationSerializerFixture.getClass.getName.stripSuffix("$"), tree.get("enumClass").asText())
    assertEquals("Running", tree.get("value").asText())
  }
}

object EnumerationSerializerFixture extends Enumeration {
  val Waiting: Value = Value("Waiting")
  val Running: Value = Value("Running")
  val Finished: Value = Value("Finished")
}

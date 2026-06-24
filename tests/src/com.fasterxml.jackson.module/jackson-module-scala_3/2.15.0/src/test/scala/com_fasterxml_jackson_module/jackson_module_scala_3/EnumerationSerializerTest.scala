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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EnumerationSerializerTest {
  private val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  @Test
  def serializesLegacyScalaEnumerationObjectShape(): Unit = {
    val json: String = mapper.writeValueAsString(EnumerationSerializerFixture.Running)
    val tree: JsonNode = mapper.readTree(json)

    val expectedEnumClass: String = EnumerationSerializerFixture.getClass.getName.stripSuffix("$")

    assertThat(tree.get("enumClass").asText()).isEqualTo(expectedEnumClass)
    assertThat(tree.get("value").asText()).isEqualTo("Running")
  }
}

object EnumerationSerializerFixture extends Enumeration {
  val Waiting: Value = Value("Waiting")
  val Running: Value = Value("Running")
  val Finished: Value = Value("Finished")
}

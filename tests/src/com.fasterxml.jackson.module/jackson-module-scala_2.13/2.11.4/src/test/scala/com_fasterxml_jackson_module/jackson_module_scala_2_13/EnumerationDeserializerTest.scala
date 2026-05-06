/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnumerationDeserializerTest {
  @Test
  def deserializesLegacyScalaEnumerationObjectShape(): Unit = {
    val mapper: ObjectMapper with ScalaObjectMapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    val enumClassName: String = EnumerationDeserializerFixture.getClass.getName.stripSuffix("$")
    val json: String = s"""{"enumClass":"$enumClassName","value":"Medium"}"""

    val value: EnumerationDeserializerFixture.Value = mapper.readValue[EnumerationDeserializerFixture.Value](json)

    assertEquals(EnumerationDeserializerFixture.Medium, value)
  }
}

object EnumerationDeserializerFixture extends Enumeration {
  val Low: Value = Value("Low")
  val Medium: Value = Value("Medium")
  val High: Value = Value("High")
}

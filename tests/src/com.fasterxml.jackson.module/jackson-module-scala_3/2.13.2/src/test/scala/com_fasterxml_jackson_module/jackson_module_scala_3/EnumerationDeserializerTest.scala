/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_3

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EnumerationDeserializerTest {
  private val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  @Test
  def deserializesLegacyScalaEnumerationObjectShape(): Unit = {
    val enumClassName: String = EnumerationDeserializerFixture.getClass.getName.stripSuffix("$")
    val json: String = s"""{"enumClass":"$enumClassName","value":"Medium"}"""

    val value: scala.Enumeration#Value = mapper.readValue(json, classOf[scala.Enumeration#Value])

    assertThat(value).isEqualTo(EnumerationDeserializerFixture.Medium)
  }
}

object EnumerationDeserializerFixture extends Enumeration {
  val Low: Value = Value("Low")
  val Medium: Value = Value("Medium")
  val High: Value = Value("High")
}

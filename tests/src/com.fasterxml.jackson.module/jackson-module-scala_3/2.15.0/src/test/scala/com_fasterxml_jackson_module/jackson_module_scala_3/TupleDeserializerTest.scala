/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_3

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TupleDeserializerTest {
  private val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  @Test
  def deserializesParameterizedScalaTupleFromArray(): Unit = {
    val value: (String, Int) =
      mapper.readValue("""["Ada", 7]""", new TypeReference[(String, Int)] {})

    assertThat(value).isEqualTo(("Ada", 7))
  }
}

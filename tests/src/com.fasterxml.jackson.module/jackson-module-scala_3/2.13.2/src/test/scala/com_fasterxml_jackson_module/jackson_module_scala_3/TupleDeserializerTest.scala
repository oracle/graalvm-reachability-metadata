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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TupleDeserializerTest {
  @Test
  def deserializesTypedScalaTupleFromJsonArray(): Unit = {
    val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
    val tupleType: TypeReference[(Int, String)] = new TypeReference[(Int, String)] {}

    val value: (Int, String) = mapper.readValue("""[42,"answer"]""", tupleType)

    assertEquals((42, "answer"), value)
  }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_3

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.deser.ScalaObjectDeserializerModule
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ScalaObjectDeserializerTest {
  @Test
  def deserializesScalaObjectSingleton(): Unit = {
    val mapper: ObjectMapper = new ObjectMapper().registerModule(ScalaObjectDeserializerModule)

    val value: AnyRef = mapper.readValue("{}", ScalaObjectDeserializerTarget.getClass)

    assertSame(ScalaObjectDeserializerTarget, value)
  }
}

object ScalaObjectDeserializerTarget

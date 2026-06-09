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
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.annotation.meta.field

class EnumResolverTest {
  private val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  @Test
  def deserializesAnnotatedEnumerationValueFromString(): Unit = {
    val value: EnumResolverEnvelope =
      mapper.readValue("""{"state":"Published"}""", classOf[EnumResolverEnvelope])

    assertThat(value.state).isEqualTo(EnumResolverFixture.Published)
  }
}

object EnumResolverFixture extends Enumeration {
  val Draft: Value = Value("Draft")
  val Published: Value = Value("Published")
}

class EnumResolverFixtureType extends TypeReference[EnumResolverFixture.type]

class EnumResolverEnvelope {
  @(JsonScalaEnumeration @field)(classOf[EnumResolverFixtureType])
  var state: EnumResolverFixture.Value = EnumResolverFixture.Draft
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_core_3

import org.json4s.DefaultFormats
import org.json4s.Extraction
import org.json4s.FieldSerializer
import org.json4s.Formats
import org.json4s.JInt
import org.json4s.JString
import org.json4s.JValue
import org.json4s.jvalue2monadic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

case class ExtractionSerializablePerson(name: String, age: Int)

class ExtractionLazyValSubject {
  val token: String = "field-value"

  def `token$lzycompute`(): String = "lazy-value"
}

class ExtractionTest {
  @Test
  def decomposesCaseClassThroughObjectPropertyDiscovery(): Unit = {
    implicit val formats: Formats = DefaultFormats

    val json: JValue = Extraction.decompose(ExtractionSerializablePerson("Ada", 37))

    assertEquals(JString("Ada"), json \ "name")
    assertEquals(JInt(37), json \ "age")
  }

  @Test
  def decomposesFieldSerializerLazyValThroughLazyComputeMethod(): Unit = {
    implicit val formats: Formats = DefaultFormats + FieldSerializer[ExtractionLazyValSubject](includeLazyVal = true)

    val json: JValue = Extraction.decompose(new ExtractionLazyValSubject)

    assertEquals(JString("lazy-value"), json \ "token")
  }
}

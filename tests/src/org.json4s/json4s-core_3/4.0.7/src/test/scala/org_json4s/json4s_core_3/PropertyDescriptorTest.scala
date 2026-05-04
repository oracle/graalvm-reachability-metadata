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
import org.json4s.JField
import org.json4s.JInt
import org.json4s.JObject
import org.json4s.JString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PropertyDescriptorMutableSubject {
  var name: String = "initial"
  var count: Int = -1
}

class PropertyDescriptorTest {
  @Test
  def extractsJsonIntoMutableFieldsUsingFieldSerializer(): Unit = {
    implicit val formats: Formats = DefaultFormats + FieldSerializer[PropertyDescriptorMutableSubject]()
    val json: JObject = JObject(
      List(
        JField("name", JString("configured")),
        JField("count", JInt(11))
      )
    )

    val extracted: PropertyDescriptorMutableSubject = Extraction.extract[PropertyDescriptorMutableSubject](json)

    assertEquals("configured", extracted.name)
    assertEquals(11, extracted.count)
  }
}

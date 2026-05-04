/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_core_3

import org.json4s.DefaultFormats
import org.json4s.Extraction
import org.json4s.Formats
import org.json4s.JArray
import org.json4s.JInt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExtractionInnerCollectionBuilderTest {
  @Test
  def extractsJsonArrayIntoTypedScalaArray(): Unit = {
    implicit val formats: Formats = DefaultFormats
    val json: JArray = JArray(List(JInt(2), JInt(3), JInt(5)))

    val values: Array[Int] = Extraction.extract[Array[Int]](json)

    assertEquals(List(2, 3, 5), values.toList)
  }
}

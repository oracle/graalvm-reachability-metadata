/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_core_2_13

import org.assertj.core.api.Assertions.assertThat
import org.json4s.DefaultFormats
import org.json4s.Extraction
import org.json4s.Formats
import org.json4s.JArray
import org.json4s.JString
import org.junit.jupiter.api.Test

class CompatTest {
  @Test
  def extractsVectorUsingCompanionBuilder(): Unit = {
    implicit val formats: Formats = DefaultFormats
    val json: JArray = JArray(List(JString("alpha"), JString("beta"), JString("gamma")))

    val extracted: Vector[String] = Extraction.extract[Vector[String]](json)

    assertThat(extracted).isEqualTo(Vector("alpha", "beta", "gamma"))
  }
}

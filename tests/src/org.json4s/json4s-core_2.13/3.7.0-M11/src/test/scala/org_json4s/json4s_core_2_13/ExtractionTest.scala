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
import org.json4s.FieldSerializer
import org.json4s.Formats
import org.json4s.JValue
import org.junit.jupiter.api.Test

class ExtractionTest {
  @Test
  def decomposesCaseClassUsingPublicAccessors(): Unit = {
    implicit val formats: Formats = DefaultFormats
    val json: JValue = Extraction.decompose(ExtractionRecord(7, "seven"))

    assertThat((json \ "id").values).isEqualTo(7)
    assertThat((json \ "name").values).isEqualTo("seven")
  }

  @Test
  def decomposesLazyValWhenFieldSerializerIncludesLazyValues(): Unit = {
    implicit val formats: Formats = DefaultFormats + FieldSerializer[ExtractionLazyValFixture](includeLazyVal = true)
    val fixture: ExtractionLazyValFixture = new ExtractionLazyValFixture("alpha")

    val json: JValue = Extraction.decompose(fixture)

    assertThat((json \ "name").values).isEqualTo("alpha")
    assertThat((json \ "computed").values).isEqualTo("computed-alpha")
  }
}

case class ExtractionRecord(id: Int, name: String)

class ExtractionLazyValFixture(val name: String) {
  lazy val computed: String = s"computed-$name"
}

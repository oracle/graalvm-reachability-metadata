/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_core_2_13

import org.assertj.core.api.Assertions.assertThat
import org.json4s.reflect.ScalaType
import org.junit.jupiter.api.Test

class ScalaTypeTest {
  @Test
  def resolvesSingletonInstanceForScalaObject(): Unit = {
    val scalaType: ScalaType = ScalaType(ScalaTypeSingletonFixture.getClass)
    val singletonInstance: Option[AnyRef] = scalaType.singletonInstance

    assertThat(scalaType.isSingleton).isTrue()
    assertThat(singletonInstance.isDefined).isTrue()
    assertThat(singletonInstance.get).isSameAs(ScalaTypeSingletonFixture)
  }
}

object ScalaTypeSingletonFixture {
  val name: String = "json4s-singleton"
}

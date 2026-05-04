/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_core_3

import org.json4s.reflect.ScalaType
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

object ScalaTypeSingletonSubject {
  val label: String = "singleton"
}

class ScalaTypeTest {
  @Test
  def resolvesScalaObjectSingletonInstanceFromModuleField(): Unit = {
    val scalaType: ScalaType = ScalaType(ScalaTypeSingletonSubject.getClass)
    val singletonInstance: Option[AnyRef] = scalaType.singletonInstance

    assertTrue(scalaType.isSingleton)
    assertTrue(singletonInstance.isDefined)
    assertSame(ScalaTypeSingletonSubject, singletonInstance.get)
  }
}

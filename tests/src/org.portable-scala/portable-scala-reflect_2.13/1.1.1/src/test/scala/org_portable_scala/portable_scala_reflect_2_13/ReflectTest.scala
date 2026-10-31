/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_portable_scala.portable_scala_reflect_2_13

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.portablescala.reflect.Reflect

class ReflectTest {
  @Test
  def rejectsAnOrdinaryAnnotatedClassAsAModule(): Unit = {
    assertTrue(Reflect.lookupLoadableModuleClass(
      classOf[InstantiatableClassTest.Message].getName, getClass.getClassLoader).isEmpty)
  }
}

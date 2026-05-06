/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_3

import com.fasterxml.jackson.module.scala.util.EnumResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnumResolverTest {
  @Test
  def resolvesEnumerationFromScalaObjectClass(): Unit = {
    val enumClass: Class[Enumeration] = ResolverTrafficLight.getClass.asInstanceOf[Class[Enumeration]]

    val resolver: EnumResolver = EnumResolver(enumClass)

    assertEquals(ResolverTrafficLight.getClass, resolver.getEnumClass)
    assertEquals(ResolverTrafficLight.Green, resolver.getEnum("Green"))
  }
}

object ResolverTrafficLight extends Enumeration {
  val Red: Value = Value("Red")
  val Green: Value = Value("Green")
  val Yellow: Value = Value("Yellow")
}

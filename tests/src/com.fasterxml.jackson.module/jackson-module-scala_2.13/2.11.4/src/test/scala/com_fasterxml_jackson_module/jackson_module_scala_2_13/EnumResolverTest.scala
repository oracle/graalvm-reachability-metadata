/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.module.scala.util.EnumResolver
import org.junit.jupiter.api.Assertions.{assertEquals, assertSame}
import org.junit.jupiter.api.Test

class EnumResolverTest {
  @Test
  def resolvesScalaEnumerationFromModuleClass(): Unit = {
    val resolver: EnumResolver = EnumResolver(EnumResolverFixture.getClass)

    assertSame(EnumResolverFixture.getClass, resolver.getEnumClass)
    assertEquals(EnumResolverFixture.Green, resolver.getEnum("green"))
  }
}

object EnumResolverFixture extends Enumeration {
  val Red: Value = Value("red")
  val Green: Value = Value("green")
  val Blue: Value = Value("blue")
}

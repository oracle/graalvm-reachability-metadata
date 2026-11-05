/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_portable_scala.portable_scala_reflect_2_13

import org.junit.jupiter.api.Assertions.{assertEquals, assertSame}
import org.junit.jupiter.api.Test
import org.portablescala.reflect.{LoadableModuleClass, Reflect}
import org.portablescala.reflect.annotation.EnableReflectiveInstantiation

class LoadableModuleClassTest {
  @Test
  def discoversAndLoadsAnAnnotatedScalaObject(): Unit = {
    val moduleName: String =
      "org_portable_scala.portable_scala_reflect_2_13.GreetingModule$"
    val descriptor: LoadableModuleClass =
      Reflect.lookupLoadableModuleClass(moduleName, getClass.getClassLoader).get
    val module: GreetingModule.type = descriptor.loadModule().asInstanceOf[GreetingModule.type]

    assertEquals(moduleName, descriptor.runtimeClass.getName)
    assertEquals("Hello, Ada!", module.greet("Ada"))
    assertSame(GreetingModule, module)
    assertSame(module, descriptor.loadModule())
  }
}

@EnableReflectiveInstantiation
object GreetingModule {
  def greet(name: String): String = s"Hello, $name!"
}

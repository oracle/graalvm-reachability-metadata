/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.util.concurrent.Callable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class TypesTest {
  @Test
  def createsArrayClassesForComponentClasses(): Unit = {
    val getArrayClass: Method = typesClass.getDeclaredMethod("getArrayClass", classOf[Class[?]])
    getArrayClass.setAccessible(true)

    val arrayClass: AnyRef = getArrayClass.invoke(null, classOf[String])

    assertSame(classOf[Array[String]], arrayClass)
  }

  @Test
  def createsInterfaceProxiesWithSuppliedInvocationHandlers(): Unit = {
    val newProxy: Method = typesClass.getDeclaredMethod(
      "newProxy",
      classOf[Class[?]],
      classOf[InvocationHandler]
    )
    newProxy.setAccessible(true)
    val handler: InvocationHandler = (proxy: Object, method: Method, args: Array[Object]) => {
      method.getName match {
        case "call" => "from-proxy"
        case "toString" => "TypesTest proxy"
        case "hashCode" => Integer.valueOf(System.identityHashCode(proxy))
        case "equals" => Boolean.box(proxy eq args.apply(0))
        case other => throw new UnsupportedOperationException(other)
      }
    }

    val proxy: Callable[String] = newProxy
      .invoke(null, classOf[Callable[?]], handler)
      .asInstanceOf[Callable[String]]

    assertEquals("from-proxy", proxy.call())
  }

  private def typesClass: Class[?] = Class.forName("org.joda.convert.Types")
}

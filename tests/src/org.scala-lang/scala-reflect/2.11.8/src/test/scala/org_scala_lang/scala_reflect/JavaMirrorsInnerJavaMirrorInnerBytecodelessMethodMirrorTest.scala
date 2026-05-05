/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_reflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.reflect.runtime.universe.MethodSymbol
import scala.reflect.runtime.universe.TermName
import scala.reflect.runtime.universe.Type
import scala.reflect.runtime.universe.newTermName
import scala.reflect.runtime.universe.runtimeMirror
import scala.reflect.runtime.universe.typeOf

class JavaMirrorsInnerJavaMirrorInnerBytecodelessMethodMirrorTest {
  @Test
  def invokesPrimitiveValueClassMethodThroughBytecodelessMethodMirror(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val instanceMirror = mirror.reflect(42)
    val toLongMethod = singleMethod(typeOf[Int], newTermName("toLong"))

    val result = instanceMirror.reflectMethod(toLongMethod)()

    assertThat(result).isEqualTo(42L)
  }

  private def singleMethod(ownerType: Type, name: TermName): MethodSymbol =
    ownerType.member(name).asMethod
}

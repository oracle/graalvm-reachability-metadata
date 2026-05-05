/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_reflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.reflect.runtime.universe.ClassSymbol
import scala.reflect.runtime.universe.MethodSymbol
import scala.reflect.runtime.universe.TermName
import scala.reflect.runtime.universe.Type
import scala.reflect.runtime.universe.TypeName
import scala.reflect.runtime.universe.newTermName
import scala.reflect.runtime.universe.newTypeName
import scala.reflect.runtime.universe.runtimeMirror
import scala.reflect.runtime.universe.termNames
import scala.reflect.runtime.universe.typeOf

class JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror0Test {
  @Test
  def invokesZeroArgumentMethodThroughVanillaMethodMirror0(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val subject = new JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror0Fixtures.ZeroArgumentMethods("alpha")
    val subjectType = typeOf[JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror0Fixtures.ZeroArgumentMethods]
    val instanceMirror = mirror.reflect(subject)

    val markerMethod = instanceMirror.reflectMethod(singleMethod(subjectType, newTermName("marker")))
    val result = markerMethod().asInstanceOf[String]

    assertThat(result).isEqualTo("marker:alpha")
  }

  @Test
  def invokesZeroArgumentConstructorThroughVanillaMethodMirror0(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val subjectClass = mirror.staticClass(
      "org_scala_lang.scala_reflect.JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror0Fixtures.ZeroArgumentConstructed"
    )
    val constructor = singleMethod(subjectClass.toType, termNames.CONSTRUCTOR)
    val classMirror = mirror.reflectClass(subjectClass)

    val constructed = classMirror.reflectConstructor(constructor)()
      .asInstanceOf[JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror0Fixtures.ZeroArgumentConstructed]

    assertThat(constructed.marker).isEqualTo("constructed")
  }

  @Test
  def invokesZeroArgumentInnerConstructorThroughVanillaMethodMirror0(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val outer = new JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror0Fixtures.Outer("owner")
    val outerType = typeOf[JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror0Fixtures.Outer]
    val innerClass = singleClass(outerType, newTypeName("Inner"))
    val constructor = singleMethod(innerClass.toType, termNames.CONSTRUCTOR)
    val outerMirror = mirror.reflect(outer)

    val inner = outerMirror.reflectClass(innerClass).reflectConstructor(constructor)()
      .asInstanceOf[outer.Inner]

    assertThat(inner.marker).isEqualTo("inner:owner")
  }

  private def singleMethod(ownerType: Type, name: TermName): MethodSymbol =
    ownerType.decl(name).asMethod

  private def singleClass(ownerType: Type, name: TypeName): ClassSymbol =
    ownerType.decl(name).asClass
}

object JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror0Fixtures {
  class ZeroArgumentMethods(val value: String) {
    def marker(): String = s"marker:$value"
  }

  class ZeroArgumentConstructed() {
    def marker: String = "constructed"
  }

  class Outer(val owner: String) {
    class Inner() {
      def marker: String = s"inner:$owner"
    }
  }
}

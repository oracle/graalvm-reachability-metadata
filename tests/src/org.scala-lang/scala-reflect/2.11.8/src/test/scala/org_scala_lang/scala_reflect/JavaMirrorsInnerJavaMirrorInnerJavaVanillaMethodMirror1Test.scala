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

class JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror1Test {
  @Test
  def invokesOneArgumentMethodThroughVanillaMethodMirror1(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val subject = new JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror1Fixtures.OneArgumentMethods("root")
    val subjectType = typeOf[JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror1Fixtures.OneArgumentMethods]
    val instanceMirror = mirror.reflect(subject)

    val combineMethod = instanceMirror.reflectMethod(singleMethod(subjectType, newTermName("combine")))
    val result = combineMethod("leaf").asInstanceOf[String]

    assertThat(result).isEqualTo("root:leaf")
  }

  @Test
  def invokesOneArgumentConstructorThroughVanillaMethodMirror1(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val subjectClass = mirror.staticClass(
      "org_scala_lang.scala_reflect.JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror1Fixtures.OneArgumentConstructed"
    )
    val constructor = singleMethod(subjectClass.toType, termNames.CONSTRUCTOR)
    val classMirror = mirror.reflectClass(subjectClass)

    val constructed = classMirror.reflectConstructor(constructor)("created")
      .asInstanceOf[JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror1Fixtures.OneArgumentConstructed]

    assertThat(constructed.marker).isEqualTo("constructed:created")
  }

  @Test
  def invokesOneArgumentInnerConstructorThroughVanillaMethodMirror1(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val outer = new JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror1Fixtures.Outer("owner")
    val outerType = typeOf[JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror1Fixtures.Outer]
    val innerClass = singleClass(outerType, newTypeName("Inner"))
    val constructor = singleMethod(innerClass.toType, termNames.CONSTRUCTOR)
    val outerMirror = mirror.reflect(outer)

    val inner = outerMirror.reflectClass(innerClass).reflectConstructor(constructor)("child")
      .asInstanceOf[outer.Inner]

    assertThat(inner.marker).isEqualTo("inner:owner:child")
  }

  private def singleMethod(ownerType: Type, name: TermName): MethodSymbol =
    ownerType.decl(name).asMethod

  private def singleClass(ownerType: Type, name: TypeName): ClassSymbol =
    ownerType.decl(name).asClass
}

object JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror1Fixtures {
  class OneArgumentMethods(val prefix: String) {
    def combine(value: String): String = s"$prefix:$value"
  }

  class OneArgumentConstructed(value: String) {
    def marker: String = s"constructed:$value"
  }

  class Outer(val owner: String) {
    class Inner(value: String) {
      def marker: String = s"inner:$owner:$value"
    }
  }
}

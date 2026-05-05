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

class JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror3Test {
  @Test
  def invokesThreeArgumentMethodThroughVanillaMethodMirror3(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val subject = new JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror3Fixtures.ThreeArgumentMethods("root")
    val subjectType = typeOf[JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror3Fixtures.ThreeArgumentMethods]
    val instanceMirror = mirror.reflect(subject)

    val combineMethod = instanceMirror.reflectMethod(singleMethod(subjectType, newTermName("combine")))
    val result = combineMethod("a", "b", "c").asInstanceOf[String]

    assertThat(result).isEqualTo("root:a:b:c")
  }

  @Test
  def invokesThreeArgumentConstructorThroughVanillaMethodMirror3(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val subjectClass = mirror.staticClass(
      "org_scala_lang.scala_reflect.JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror3Fixtures.ThreeArgumentConstructed"
    )
    val constructor = singleMethod(subjectClass.toType, termNames.CONSTRUCTOR)
    val classMirror = mirror.reflectClass(subjectClass)

    val constructed = classMirror.reflectConstructor(constructor)("a", "b", "c")
      .asInstanceOf[JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror3Fixtures.ThreeArgumentConstructed]

    assertThat(constructed.joined).isEqualTo("a:b:c")
  }

  @Test
  def invokesThreeArgumentInnerConstructorThroughVanillaMethodMirror3(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val outer = new JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror3Fixtures.Outer("owner")
    val outerType = typeOf[JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror3Fixtures.Outer]
    val innerClass = singleClass(outerType, newTypeName("Inner"))
    val constructor = singleMethod(innerClass.toType, termNames.CONSTRUCTOR)
    val outerMirror = mirror.reflect(outer)

    val inner = outerMirror.reflectClass(innerClass).reflectConstructor(constructor)("a", "b", "c")
      .asInstanceOf[outer.Inner]

    assertThat(inner.joined).isEqualTo("owner:a:b:c")
  }

  private def singleMethod(ownerType: Type, name: TermName): MethodSymbol =
    ownerType.decl(name).asMethod

  private def singleClass(ownerType: Type, name: TypeName): ClassSymbol =
    ownerType.decl(name).asClass
}

object JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror3Fixtures {
  class ThreeArgumentMethods(val prefix: String) {
    def combine(a: String, b: String, c: String): String =
      Seq(prefix, a, b, c).mkString(":")
  }

  class ThreeArgumentConstructed(a: String, b: String, c: String) {
    def joined: String = Seq(a, b, c).mkString(":")
  }

  class Outer(val owner: String) {
    class Inner(a: String, b: String, c: String) {
      def joined: String = Seq(owner, a, b, c).mkString(":")
    }
  }
}

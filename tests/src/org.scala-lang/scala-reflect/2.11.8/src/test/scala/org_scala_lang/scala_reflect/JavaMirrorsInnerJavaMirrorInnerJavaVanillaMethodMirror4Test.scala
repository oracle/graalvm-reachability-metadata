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

class JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror4Test {
  @Test
  def invokesFourArgumentMethodThroughVanillaMethodMirror4(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val subject = new JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror4Fixtures.FourArgumentMethods("root")
    val subjectType = typeOf[JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror4Fixtures.FourArgumentMethods]
    val instanceMirror = mirror.reflect(subject)

    val combineMethod = instanceMirror.reflectMethod(singleMethod(subjectType, newTermName("combine")))
    val result = combineMethod("a", "b", "c", "d").asInstanceOf[String]

    assertThat(result).isEqualTo("root:a:b:c:d")
  }

  @Test
  def invokesFourArgumentConstructorThroughVanillaMethodMirror4(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val subjectClass = mirror.staticClass(
      "org_scala_lang.scala_reflect.JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror4Fixtures.FourArgumentConstructed"
    )
    val constructor = singleMethod(subjectClass.toType, termNames.CONSTRUCTOR)
    val classMirror = mirror.reflectClass(subjectClass)

    val constructed = classMirror.reflectConstructor(constructor)("a", "b", "c", "d")
      .asInstanceOf[JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror4Fixtures.FourArgumentConstructed]

    assertThat(constructed.joined).isEqualTo("a:b:c:d")
  }

  @Test
  def invokesFourArgumentInnerConstructorThroughVanillaMethodMirror4(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val outer = new JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror4Fixtures.Outer("owner")
    val outerType = typeOf[JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror4Fixtures.Outer]
    val innerClass = singleClass(outerType, newTypeName("Inner"))
    val constructor = singleMethod(innerClass.toType, termNames.CONSTRUCTOR)
    val outerMirror = mirror.reflect(outer)

    val inner = outerMirror.reflectClass(innerClass).reflectConstructor(constructor)("a", "b", "c", "d")
      .asInstanceOf[outer.Inner]

    assertThat(inner.joined).isEqualTo("owner:a:b:c:d")
  }

  private def singleMethod(ownerType: Type, name: TermName): MethodSymbol =
    ownerType.decl(name).asMethod

  private def singleClass(ownerType: Type, name: TypeName): ClassSymbol =
    ownerType.decl(name).asClass
}

object JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror4Fixtures {
  class FourArgumentMethods(val prefix: String) {
    def combine(a: String, b: String, c: String, d: String): String =
      Seq(prefix, a, b, c, d).mkString(":")
  }

  class FourArgumentConstructed(a: String, b: String, c: String, d: String) {
    def joined: String = Seq(a, b, c, d).mkString(":")
  }

  class Outer(val owner: String) {
    class Inner(a: String, b: String, c: String, d: String) {
      def joined: String = Seq(owner, a, b, c, d).mkString(":")
    }
  }
}

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

class JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror2Test {
  @Test
  def invokesTwoArgumentMethodThroughVanillaMethodMirror2(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val subject = new JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror2Fixtures.TwoArgumentMethods("root")
    val subjectType = typeOf[JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror2Fixtures.TwoArgumentMethods]
    val instanceMirror = mirror.reflect(subject)

    val combineMethod = instanceMirror.reflectMethod(singleMethod(subjectType, newTermName("combine")))
    val result = combineMethod("left", "right").asInstanceOf[String]

    assertThat(result).isEqualTo("root:left:right")
  }

  @Test
  def invokesTwoArgumentConstructorThroughVanillaMethodMirror2(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val subjectClass = mirror.staticClass(
      "org_scala_lang.scala_reflect.JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror2Fixtures.TwoArgumentConstructed"
    )
    val constructor = singleMethod(subjectClass.toType, termNames.CONSTRUCTOR)
    val classMirror = mirror.reflectClass(subjectClass)

    val constructed = classMirror.reflectConstructor(constructor)("left", "right")
      .asInstanceOf[JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror2Fixtures.TwoArgumentConstructed]

    assertThat(constructed.joined).isEqualTo("left:right")
  }

  @Test
  def invokesTwoArgumentInnerConstructorThroughVanillaMethodMirror2(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val outer = new JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror2Fixtures.Outer("owner")
    val outerType = typeOf[JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror2Fixtures.Outer]
    val innerClass = singleClass(outerType, newTypeName("Inner"))
    val constructor = singleMethod(innerClass.toType, termNames.CONSTRUCTOR)
    val outerMirror = mirror.reflect(outer)

    val inner = outerMirror.reflectClass(innerClass).reflectConstructor(constructor)("left", "right")
      .asInstanceOf[outer.Inner]

    assertThat(inner.joined).isEqualTo("owner:left:right")
  }

  private def singleMethod(ownerType: Type, name: TermName): MethodSymbol =
    ownerType.decl(name).asMethod

  private def singleClass(ownerType: Type, name: TypeName): ClassSymbol =
    ownerType.decl(name).asClass
}

object JavaMirrorsInnerJavaMirrorInnerJavaVanillaMethodMirror2Fixtures {
  class TwoArgumentMethods(val prefix: String) {
    def combine(left: String, right: String): String =
      Seq(prefix, left, right).mkString(":")
  }

  class TwoArgumentConstructed(left: String, right: String) {
    def joined: String = Seq(left, right).mkString(":")
  }

  class Outer(val owner: String) {
    class Inner(left: String, right: String) {
      def joined: String = Seq(owner, left, right).mkString(":")
    }
  }
}

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

class JavaMirrorsInnerJavaMirrorInnerJavaMethodMirrorTest {
  @Test
  def invokesMethodWithMoreThanFourArgumentsThroughGenericMethodMirror(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val subject = new JavaMirrorsInnerJavaMirrorInnerJavaMethodMirrorFixtures.MultiArgumentMethods("root")
    val subjectType = typeOf[JavaMirrorsInnerJavaMirrorInnerJavaMethodMirrorFixtures.MultiArgumentMethods]
    val instanceMirror = mirror.reflect(subject)

    val combineMethod = instanceMirror.reflectMethod(singleMethod(subjectType, newTermName("combine")))
    val result = combineMethod("a", "b", "c", "d", "e").asInstanceOf[String]

    assertThat(result).isEqualTo("root:a:b:c:d:e")
  }

  @Test
  def invokesTopLevelConstructorWithMoreThanFourArgumentsThroughGenericMethodMirror(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val subjectClass = mirror.staticClass(
      "org_scala_lang.scala_reflect.JavaMirrorsInnerJavaMirrorInnerJavaMethodMirrorFixtures.MultiArgumentConstructed"
    )
    val constructor = singleMethod(subjectClass.toType, termNames.CONSTRUCTOR)
    val classMirror = mirror.reflectClass(subjectClass)

    val constructed = classMirror.reflectConstructor(constructor)("a", "b", "c", "d", "e")
      .asInstanceOf[JavaMirrorsInnerJavaMirrorInnerJavaMethodMirrorFixtures.MultiArgumentConstructed]

    assertThat(constructed.joined).isEqualTo("a:b:c:d:e")
  }

  @Test
  def invokesInnerConstructorWithMoreThanFourArgumentsThroughGenericMethodMirror(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val outer = new JavaMirrorsInnerJavaMirrorInnerJavaMethodMirrorFixtures.Outer("owner")
    val outerType = typeOf[JavaMirrorsInnerJavaMirrorInnerJavaMethodMirrorFixtures.Outer]
    val innerClass = singleClass(outerType, newTypeName("Inner"))
    val constructor = singleMethod(innerClass.toType, termNames.CONSTRUCTOR)
    val outerMirror = mirror.reflect(outer)

    val inner = outerMirror.reflectClass(innerClass).reflectConstructor(constructor)("a", "b", "c", "d", "e")
      .asInstanceOf[outer.Inner]

    assertThat(inner.joined).isEqualTo("owner:a:b:c:d:e")
  }

  private def singleMethod(ownerType: Type, name: TermName): MethodSymbol =
    ownerType.decl(name).asMethod

  private def singleClass(ownerType: Type, name: TypeName): ClassSymbol =
    ownerType.decl(name).asClass
}

object JavaMirrorsInnerJavaMirrorInnerJavaMethodMirrorFixtures {
  class MultiArgumentMethods(val prefix: String) {
    def combine(a: String, b: String, c: String, d: String, e: String): String =
      Seq(prefix, a, b, c, d, e).mkString(":")
  }

  class MultiArgumentConstructed(a: String, b: String, c: String, d: String, e: String) {
    def joined: String = Seq(a, b, c, d, e).mkString(":")
  }

  class Outer(val owner: String) {
    class Inner(a: String, b: String, c: String, d: String, e: String) {
      def joined: String = Seq(owner, a, b, c, d, e).mkString(":")
    }
  }
}

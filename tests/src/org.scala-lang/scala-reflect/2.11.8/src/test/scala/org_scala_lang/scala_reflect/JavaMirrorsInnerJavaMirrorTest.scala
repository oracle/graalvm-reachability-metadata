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
import scala.reflect.runtime.universe.TermSymbol
import scala.reflect.runtime.universe.Type
import scala.reflect.runtime.universe.newTermName
import scala.reflect.runtime.universe.runtimeMirror
import scala.reflect.runtime.universe.termNames

class JavaMirrorsInnerJavaMirrorTest {
  @Test
  def runtimeMirrorLoadsTopLevelJavaAndScalaSymbols(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)

    val stringSymbol = mirror.staticClass("java.lang.String")
    val optionSymbol = mirror.staticClass("scala.Option")

    assertThat(stringSymbol.fullName).isEqualTo("java.lang.String")
    assertThat(optionSymbol.fullName).isEqualTo("scala.Option")
    assertThat(optionSymbol.toType.decls.exists(_.name.toString == "isEmpty")).isTrue()
  }

  @Test
  def reflectsNestedClassConstructorsFieldsAndMethods(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val nestedClassSymbol = mirror.staticClass(
      "org_scala_lang.scala_reflect.JavaMirrorsInnerJavaMirrorFixtures.Nested"
    )
    val nestedType = nestedClassSymbol.toType
    val constructor = singleMethod(nestedType, termNames.CONSTRUCTOR)
    val classMirror = mirror.reflectClass(nestedClassSymbol)

    val nested = classMirror.reflectConstructor(constructor)("alpha", 7)
      .asInstanceOf[JavaMirrorsInnerJavaMirrorFixtures.Nested]
    val instanceMirror = mirror.reflect(nested)

    val publicField = instanceMirror.reflectField(singleTerm(nestedType, newTermName("publicValue")))
    assertThat(publicField.get.asInstanceOf[String]).isEqualTo("alpha")

    val publicMethod = instanceMirror.reflectMethod(singleMethod(nestedType, newTermName("combine")))
    assertThat(publicMethod("id").asInstanceOf[String]).isEqualTo("id:alpha:7")

    val packageScopedField = instanceMirror.reflectField(
      singleTerm(nestedType, newTermName("packageScopedValue"))
    )
    assertThat(packageScopedField.get.asInstanceOf[String]).isEqualTo("package-alpha")
    packageScopedField.set("changed")
    assertThat(nested.packageScopedSnapshot).isEqualTo("changed")

    val packageScopedMethod = instanceMirror.reflectMethod(
      singleMethod(nestedType, newTermName("packageScopedMethod"))
    )
    assertThat(packageScopedMethod("suffix").asInstanceOf[String]).isEqualTo("changed-suffix")

    val privateField = instanceMirror.reflectField(singleTerm(nestedType, newTermName("privateValue")))
    assertThat(privateField.get.asInstanceOf[String]).isEqualTo("private-alpha")
    privateField.set("updated-private")
    assertThat(nested.privateSnapshot).isEqualTo("updated-private")

    val privateMethod = instanceMirror.reflectMethod(singleMethod(nestedType, newTermName("privateMethod")))
    assertThat(privateMethod("token").asInstanceOf[String]).isEqualTo("updated-private-token")
  }

  private def singleTerm(ownerType: Type, name: TermName): TermSymbol =
    ownerType.decl(name).asTerm

  private def singleMethod(ownerType: Type, name: TermName): MethodSymbol =
    ownerType.decl(name).asMethod
}

object JavaMirrorsInnerJavaMirrorFixtures {
  class Nested(var publicValue: String, val count: Int) {
    private[scala_reflect] var packageScopedValue: String = s"package-$publicValue"
    private var privateValue: String = s"private-$publicValue"

    def combine(prefix: String): String = s"$prefix:$publicValue:$count"

    private[scala_reflect] def packageScopedMethod(suffix: String): String =
      s"$packageScopedValue-$suffix"

    private def privateMethod(suffix: String): String = s"$privateValue-$suffix"

    def packageScopedSnapshot: String = packageScopedValue

    def privateSnapshot: String = privateValue
  }
}

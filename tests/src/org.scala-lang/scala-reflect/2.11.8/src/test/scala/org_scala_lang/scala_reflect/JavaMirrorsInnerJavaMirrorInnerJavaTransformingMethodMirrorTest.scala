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

class JavaMirrorsInnerJavaMirrorInnerJavaTransformingMethodMirrorTest {
  @Test
  def invokesReflectedMethodWithValueClassParameter(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val subject = new JavaMirrorsInnerJavaMirrorInnerJavaTransformingMethodMirrorFixtures.ValueClassConsumer
    val subjectType = typeOf[JavaMirrorsInnerJavaMirrorInnerJavaTransformingMethodMirrorFixtures.ValueClassConsumer]
    val instanceMirror = mirror.reflect(subject)

    val describeMethod = instanceMirror.reflectMethod(singleMethod(subjectType, newTermName("describe")))
    val result = describeMethod(
      JavaMirrorsInnerJavaMirrorInnerJavaTransformingMethodMirrorFixtures.Meter(42)
    ).asInstanceOf[String]

    assertThat(result).isEqualTo("distance:42")
  }

  private def singleMethod(ownerType: Type, name: TermName): MethodSymbol =
    ownerType.decl(name).asMethod
}

object JavaMirrorsInnerJavaMirrorInnerJavaTransformingMethodMirrorFixtures {
  final case class Meter(value: Int) extends AnyVal

  class ValueClassConsumer {
    def describe(distance: Meter): String = s"distance:${distance.value}"
  }
}

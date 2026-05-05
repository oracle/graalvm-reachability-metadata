/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_reflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._
import scala.reflect.runtime.universe.Annotation
import scala.reflect.runtime.universe.runtimeMirror

class JavaMirrorsInnerJavaMirrorInnerJavaAnnotationProxyTest {
  @Test
  def readsAssociationsFromJavaAnnotationProxies(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val deprecatedAnnotationSymbol = mirror.staticClass("java.lang.Deprecated")

    val associationsByAnnotation: Map[String, Map[String, String]] =
      deprecatedAnnotationSymbol.annotations.map { annotation =>
        annotation.tpe.typeSymbol.fullName -> associationStrings(annotation)
      }.toMap

    assertThat(associationsByAnnotation.keySet.asJava).contains(
      "java.lang.annotation.Retention",
      "java.lang.annotation.Target"
    )
    assertThat(associationsByAnnotation("java.lang.annotation.Retention").asJava)
      .containsEntry("value", "RUNTIME")
    val targetValue: String = associationsByAnnotation("java.lang.annotation.Target")("value")
    assertThat(targetValue).contains("METHOD")
    assertThat(targetValue).contains("TYPE")
  }

  private def associationStrings(annotation: Annotation): Map[String, String] =
    annotation.javaArgs.map { case (name, argument) => name.toString -> argument.toString }.toMap
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ghik.silencer_lib_2_13

import com.github.ghik.silencer.silent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.annotation.Annotation

final class Silencer_lib_2_13Test {
  @Test
  def constructorsCreateUsableScalaAnnotationInstances(): Unit = {
    val defaultAnnotation: Annotation = new silent()
    val patternedAnnotation: Annotation = new silent(".*unused.*")
    val emptyPatternAnnotation: Annotation = new silent("")

    assertThat(defaultAnnotation.isInstanceOf[silent]).isTrue()
    assertThat(patternedAnnotation.isInstanceOf[silent]).isTrue()
    assertThat(emptyPatternAnnotation.isInstanceOf[silent]).isTrue()
    assertThat(defaultAnnotation).isNotSameAs(patternedAnnotation)
    assertThat(patternedAnnotation).isNotSameAs(emptyPatternAnnotation)
  }

  @Test
  def annotationInstancesCanFlowThroughThePublicScalaAnnotationContract(): Unit = {
    val annotations: List[Annotation] = List(new silent(), new silent("type mismatch.*"), new NoopAnnotation)

    assertThat(annotationNames(annotations)).containsExactly("silent", "silent", "NoopAnnotation")
    assertThat(onlySilentAnnotations(annotations)).hasSize(2)
  }

  @Test
  def annotatedClassesObjectsFieldsAndMethodsKeepNormalRuntimeSemantics(): Unit = {
    val service: AnnotatedService = new AnnotatedService("svc")

    assertThat(service.marker).isEqualTo("ready")
    assertThat(service.join(List("alpha", "beta", "gamma"))).isEqualTo("svc:alpha,beta,gamma")
    assertThat(AnnotatedHelpers.format(7)).isEqualTo("value=7")
    assertThat(AnnotatedHelpers.defaultService.join(List("one"))).isEqualTo("default:one")
  }

  @Test
  def annotatedLocalDefinitionsLambdasAndPatternBindingsAreExecutable(): Unit = {
    @silent("local value used to exercise annotation placement")
    val numbers: List[Int] = List(1, 2, 3, 4)

    @silent("local function")
    def square(value: Int): Int = value * value

    val rendered: String = numbers
      .map(square)
      .collect {
        case evenSquare if evenSquare % 2 == 0 =>
          @silent("local value inside partial function")
          val label: String = s"even:$evenSquare"
          label
      }
      .mkString("|")

    assertThat(rendered).isEqualTo("even:4|even:16")
  }

  @Test
  def annotatedGenericTypesAndAliasesRemainTypeTransparent(): Unit = {
    val holder: AnnotatedHolder[String] = AnnotatedHolder("item")
    val aliases: AnnotatedHelpers.Aliases = List("left", "right")

    assertThat(holder.value).isEqualTo("item")
    assertThat(holder.map(_.toUpperCase).value).isEqualTo("ITEM")
    assertThat(AnnotatedHelpers.aliasSummary(aliases)).isEqualTo("left/right")
  }

  private def annotationNames(annotations: List[Annotation]): java.util.List[String] = {
    val names: java.util.ArrayList[String] = new java.util.ArrayList[String]()
    annotations.foreach {
      case _: silent => names.add("silent")
      case _: NoopAnnotation => names.add("NoopAnnotation")
      case _ => names.add("unknown")
    }
    names
  }

  private def onlySilentAnnotations(annotations: List[Annotation]): java.util.List[silent] = {
    val result: java.util.ArrayList[silent] = new java.util.ArrayList[silent]()
    annotations.foreach {
      case annotation: silent => result.add(annotation)
      case _ => ()
    }
    result
  }

  private final class NoopAnnotation extends Annotation

  @silent("class-level annotation")
  private final class AnnotatedService(
    @silent("constructor parameter") private val prefix: String
  ) {
    @silent("field-level annotation")
    val marker: String = "ready"

    @silent("method-level annotation")
    def join(@silent("method parameter") values: List[String]): String = s"$prefix:${values.mkString(",")}"
  }

  @silent("companion-like object annotation")
  private object AnnotatedHelpers {
    @silent("type alias annotation")
    type Aliases = List[String]

    @silent("object field annotation")
    val defaultService: AnnotatedService = new AnnotatedService("default")

    @silent("object method annotation")
    def format(@silent("object method parameter") value: Int): String = s"value=$value"

    @silent("method using annotated type alias")
    def aliasSummary(values: Aliases): String = values.mkString("/")
  }

  @silent("case class annotation")
  private final case class AnnotatedHolder[A](@silent("case class parameter") value: A) {
    @silent("generic method annotation")
    def map[B](@silent("generic function parameter") transform: A => B): AnnotatedHolder[B] =
      AnnotatedHolder(transform(value))
  }
}

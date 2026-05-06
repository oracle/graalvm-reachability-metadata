/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ghik.silencer_lib_2_13_5

import com.github.ghik.silencer.silent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.annotation.Annotation

class Silencer_lib_2_13_5Test {
  @Test
  def constructorsCreateIndependentScalaAnnotationInstances(): Unit = {
    val defaultAnnotation: silent = new silent()
    val emptyPatternAnnotation: silent = new silent("")
    val warningPatternAnnotation: silent = new silent("(?s).*local val unusedValue in method.*")
    val nullPatternAnnotation: silent = new silent(null)

    assertTrue(defaultAnnotation.isInstanceOf[Annotation])
    assertTrue(emptyPatternAnnotation.isInstanceOf[Annotation])
    assertTrue(warningPatternAnnotation.isInstanceOf[Annotation])
    assertTrue(nullPatternAnnotation.isInstanceOf[Annotation])
    assertSame(defaultAnnotation, defaultAnnotation)
    assertNotSame(defaultAnnotation, emptyPatternAnnotation)
    assertNotSame(emptyPatternAnnotation, warningPatternAnnotation)
  }

  @Test
  def annotationAcceptsRepresentativeCompilerWarningPatterns(): Unit = {
    val unusedValuePattern: silent = new silent(".*\\bunused\\b.*")
    val alternationPattern: silent = new silent(".*(deprecated|feature|unchecked).*")
    val quotedPattern: silent = new silent("\\Qmethod foo in class Bar is deprecated\\E")
    val multilinePattern: silent = new silent("(?s).*discarded non-Unit value.*")

    assertTrue(unusedValuePattern.isInstanceOf[Annotation])
    assertTrue(alternationPattern.isInstanceOf[Annotation])
    assertTrue(quotedPattern.isInstanceOf[Annotation])
    assertTrue(multilinePattern.isInstanceOf[Annotation])
  }

  @Test
  def annotatedMembersKeepNormalRuntimeSemantics(): Unit = {
    val fixture: SilencerAnnotatedFixture = new SilencerAnnotatedFixture("metadata")

    assertEquals("Hello, metadata!", fixture.greeting("!"))
    assertEquals(42, fixture.transform(41)(_ + 1))
    assertEquals(List("alpha", "beta", "gamma"), fixture.sortedLabels("gamma", "alpha", "beta"))
    assertEquals("NATIVE IMAGE", SilencerAnnotatedFixture.normalize("native image"))
  }

  @Test
  def annotationCanBeUsedOnLocalDefinitionsInsideExecutableCode(): Unit = {
    @silent("local immutable value")
    val baseValues: List[Int] = List(1, 2, 3)

    @silent("local method")
    def scale(value: Int): Int = {
      @silent("local derived value")
      val doubled: Int = value * 2
      doubled + 1
    }

    @silent("local class")
    final class LocalAccumulator(private val seed: Int) {
      def apply(values: List[Int]): Int = values.foldLeft(seed)(_ + _)
    }

    val transformed: List[Int] = baseValues.map(scale)
    val accumulator: LocalAccumulator = new LocalAccumulator(10)

    assertEquals(List(3, 5, 7), transformed)
    assertEquals(25, accumulator(transformed))
  }

  @Test
  def annotationCanBeUsedOnExpressionsWithoutChangingEvaluation(): Unit = {
    val blockResult: Int = ({
      val values: Vector[Int] = Vector(2, 4, 8)
      values.foldLeft(1)(_ + _)
    }: @silent("block expression annotation"))
    val methodCallResult: String = (Vector("native", "image").mkString("-"): @silent("method call expression annotation"))
    val booleanResult: Boolean = ({
      val observed: Option[String] = Some("silencer")
      observed.exists(_.startsWith("silent"))
    }: @silent("boolean expression annotation"))

    assertEquals(15, blockResult)
    assertEquals("native-image", methodCallResult)
    assertFalse(booleanResult)
  }

  @Test
  def annotationCanBeUsedOnTypeAliasesWithoutChangingTypeSemantics(): Unit = {
    val fixture: SilencerTypeAliasFixture = new SilencerTypeAliasFixture()
    val label: fixture.Label = "metadata"
    val values: fixture.Values[Int] = Vector(1, 2, 3)
    val namedValues: fixture.NamedValues = Map("first" -> 1, "second" -> 2)

    assertEquals("metadata:6", fixture.describe(label, values))
    assertEquals(3, fixture.total(namedValues))
  }

  @Test
  def annotatedGenericFixturePreservesCollectionAndFunctionBehavior(): Unit = {
    val pipeline: SilencerPipeline[Int] = SilencerPipeline(5)
      .map(_ + 2)
      .map(_ * 3)

    assertEquals(21, pipeline.value)
    assertEquals("21", pipeline.map(_.toString).value)
  }
}

@silent("class-level silencer annotation")
final class SilencerAnnotatedFixture(@silent("constructor parameter annotation") val name: String) {
  @silent("field annotation")
  val prefix: String = "Hello"

  @silent("method annotation")
  def greeting(@silent("method parameter annotation") punctuation: String): String = {
    s"$prefix, $name$punctuation"
  }

  @silent("generic higher-order method annotation")
  def transform(value: Int)(operation: Int => Int): Int = {
    operation(value)
  }

  @silent("varargs method annotation")
  def sortedLabels(values: String*): List[String] = {
    values.toList.sorted
  }
}

object SilencerAnnotatedFixture {
  @silent("companion method annotation")
  def normalize(value: String): String = {
    value.toUpperCase(java.util.Locale.ROOT)
  }
}

final class SilencerTypeAliasFixture {
  @silent("type alias annotation")
  type Label = String

  @silent("generic type alias annotation")
  type Values[A] = Vector[A]

  @silent("nested type alias annotation")
  type NamedValues = Map[String, Int]

  def describe(label: Label, values: Values[Int]): String = {
    s"$label:${values.sum}"
  }

  def total(values: NamedValues): Int = {
    values.values.sum
  }
}

@silent("trait annotation")
sealed trait SilencerPipeline[A] {
  @silent("abstract method annotation")
  def value: A

  @silent("generic abstract method annotation")
  def map[B](operation: A => B): SilencerPipeline[B]
}

object SilencerPipeline {
  def apply[A](initialValue: A): SilencerPipeline[A] = {
    ValuePipeline(initialValue)
  }

  @silent("case class annotation")
  private final case class ValuePipeline[A](@silent("case class parameter annotation") value: A) extends SilencerPipeline[A] {
    override def map[B](operation: A => B): SilencerPipeline[B] = {
      ValuePipeline(operation(value))
    }
  }
}

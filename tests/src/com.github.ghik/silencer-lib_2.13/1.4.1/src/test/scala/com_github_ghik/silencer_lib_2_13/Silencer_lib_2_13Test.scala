/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ghik.silencer_lib_2_13

import com.github.ghik.silencer.silent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.annotation.Annotation

class Silencer_lib_2_13Test {
  @Test
  def constructorsCreateScalaAnnotationInstances(): Unit = {
    val withoutPattern: silent = new silent()
    val withPattern: silent = new silent(".*unused value.*")

    assertTrue(withoutPattern.isInstanceOf[Annotation])
    assertTrue(withPattern.isInstanceOf[Annotation])
    assertSame(withoutPattern, withoutPattern)
    assertNotSame(withoutPattern, withPattern)
  }

  @Test
  def messagePatternConstructorAcceptsRepresentativeCompilerWarningPatterns(): Unit = {
    val emptyPattern: silent = new silent("")
    val quotedWarningPattern: silent = new silent("(?s).*local val unusedValue in method.*")
    val alternationPattern: silent = new silent(".*(deprecated|feature|unchecked).*")

    assertTrue(emptyPattern.isInstanceOf[Annotation])
    assertTrue(quotedWarningPattern.isInstanceOf[Annotation])
    assertTrue(alternationPattern.isInstanceOf[Annotation])
  }

  @Test
  def annotatedDefinitionsKeepNormalScalaSemantics(): Unit = {
    val fixture: SilencerAnnotatedFixture = new SilencerAnnotatedFixture("metadata")

    assertEquals("Hello, metadata!", fixture.greeting("!"))
    assertEquals(42, fixture.transform(41)(_ + 1))
    assertEquals("NATIVE IMAGE", SilencerAnnotatedFixture.normalize("native image"))
  }

  @Test
  def annotationCanBeUsedOnLocalDefinitionsInsideExecutableCode(): Unit = {
    @silent("local immutable value")
    val baseValues: List[Int] = List(1, 2, 3)

    val transformed: List[Int] = baseValues.map { value: Int =>
      @silent("local derived value")
      val doubled: Int = value * 2
      doubled + 1
    }

    assertEquals(List(3, 5, 7), transformed)
  }

  @Test
  def annotationCanBeUsedOnExpressionsWithoutChangingEvaluation(): Unit = {
    val blockResult: Int = ({
      val values: Vector[Int] = Vector(2, 4, 8)
      values.foldLeft(1)(_ + _)
    }: @silent("block expression annotation"))
    val methodCallResult: String = (Vector("native", "image").mkString("-"): @silent("method call expression annotation"))

    assertEquals(15, blockResult)
    assertEquals("native-image", methodCallResult)
  }

  @Test
  def annotationCanBeUsedOnTypeAliasesWithoutChangingTypeSemantics(): Unit = {
    val fixture: SilencerTypeAliasFixture = new SilencerTypeAliasFixture()
    val label: fixture.Label = "metadata"
    val values: fixture.Values[Int] = Vector(1, 2, 3)

    assertEquals("metadata:6", fixture.describe(label, values))
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

  @silent("generic method annotation")
  def transform(value: Int)(operation: Int => Int): Int = {
    operation(value)
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

  def describe(label: Label, values: Values[Int]): String = {
    s"$label:${values.sum}"
  }
}

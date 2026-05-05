/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ghik.silencer_lib_2_13_16

import com.github.ghik.silencer.silent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.util.Locale
import scala.annotation.Annotation

class Silencer_lib_2_13_16Test {
  @Test
  def constructorsCreateIndependentScalaAnnotationInstances(): Unit = {
    val withoutPattern: silent = new silent()
    val emptyPattern: silent = new silent("")
    val warningPattern: silent = new silent("(?s).*local value unusedValue is never used.*")
    val alternationPattern: silent = new silent(".*(deprecated|feature|unchecked).*warning.*")

    assertTrue(withoutPattern.isInstanceOf[Annotation])
    assertTrue(emptyPattern.isInstanceOf[Annotation])
    assertTrue(warningPattern.isInstanceOf[Annotation])
    assertTrue(alternationPattern.isInstanceOf[Annotation])
    assertSame(warningPattern, warningPattern)
    assertNotSame(withoutPattern, emptyPattern)
    assertNotSame(warningPattern, alternationPattern)
  }

  @Test
  def silentAnnotationSuppressesFatalUnusedLocalCompilerWarning(): Unit = {
    assertEquals(42, SilencedWarningFixture.valueAfterSuppressedCompilerWarning())
  }

  @Test
  def classObjectFieldMethodAndParameterAnnotationsPreserveRuntimeSemantics(): Unit = {
    val fixture: SilencerAnnotatedFixture = new SilencerAnnotatedFixture(" Native Image ")

    assertEquals("Hello, Native Image!", fixture.greeting("!"))
    assertEquals("native image", fixture.normalizedName)
    assertEquals(42, fixture.transform(41)(_ + 1))
    assertEquals("NATIVE IMAGE", SilencerAnnotatedFixture.normalize("native image"))

    fixture.suffix = "?"
    assertEquals("Hello, Native Image?", fixture.greeting(fixture.suffix))
  }

  @Test
  def annotationWorksOnTraitsImplementationsAndCaseClasses(): Unit = {
    val increment: SilencerOperation[Int, Int] = new IncrementOperation()
    val decorated: SilencerResult[Int] = SilencerResult("metadata", 8).mapLabel(_.toUpperCase(Locale.ROOT))

    assertEquals(42, increment(41))
    assertEquals("METADATA", decorated.label)
    assertEquals(8, decorated.value)
    assertEquals("METADATA:8", decorated.describe)
  }

  @Test
  def localDefinitionAndExpressionAnnotationsDoNotChangeEvaluation(): Unit = {
    @silent("local collection used to verify expression evaluation")
    val baseValues: Vector[Int] = Vector(2, 4, 8)

    val folded: Int = ({
      @silent("local accumulator seed")
      val seed: Int = 1
      baseValues.foldLeft(seed)(_ + _)
    }: @silent("block expression annotation"))

    val rendered: String = (baseValues.map(_ / 2).mkString("-"): @silent("method call expression annotation"))

    assertEquals(15, folded)
    assertEquals("1-2-4", rendered)
  }

  @Test
  def annotationWorksOnTypeAliasesAndNestedGenericValues(): Unit = {
    val aliases: SilencerTypeAliasFixture = new SilencerTypeAliasFixture()
    val label: aliases.Label = "metadata"
    val groupedValues: aliases.Grouped[Int] = Map("odd" -> Vector(1, 3), "even" -> Vector(2, 4))

    assertEquals("metadata:10", aliases.describe(label, groupedValues))
  }

  @Test
  def annotatedHigherOrderDefinitionsKeepNormalCollectionAndOptionSemantics(): Unit = {
    val pipeline: SilencerPipeline = new SilencerPipeline()
    val parsed: List[Int] = pipeline.parsePositiveIntegers(List("1", "not-a-number", "3", "0", "5"))
    val summarized: Option[String] = pipeline.summarize(parsed)

    assertEquals(List(1, 3, 5), parsed)
    assertEquals(Some("1 + 3 + 5 = 9"), summarized)
    assertFalse(pipeline.summarize(Nil).isDefined)
  }

  @Test
  def annotationsOnImplicitDefinitionsKeepImplicitResolutionAndExtensionMethods(): Unit = {
    import SilencerImplicitFixture._

    assertEquals("int:7", render(7))
    assertEquals("string:metadata", render("metadata"))
    assertEquals("some(int:3)", render(Option(3)))
    assertEquals("none", render(Option.empty[Int]))
    assertEquals("int:12", 12.rendered)
  }

  @Test
  def annotationsOnClassAndMethodTypeParametersKeepGenericContainerSemantics(): Unit = {
    val first: SilencerGenericBox[String] = SilencerGenericBox("metadata")
    val length: SilencerGenericBox[Int] = first.map(_.length)
    val combined: SilencerGenericBox[(String, Int)] = first.zip(length)

    assertEquals("metadata", first.value)
    assertEquals(8, length.value)
    assertEquals(("metadata", 8), combined.value)
    assertEquals("metadata -> 8", combined.fold { case (label, size) => s"$label -> $size" })
  }
}

object SilencedWarningFixture {
  @silent(".*unusedSilencedWarningValue.*never used.*")
  def valueAfterSuppressedCompilerWarning(): Int = {
    val unusedSilencedWarningValue: String = "silenced"

    42
  }
}

@silent("class-level silencer annotation")
final class SilencerAnnotatedFixture(@silent("constructor parameter annotation") val name: String) {
  @silent("field annotation")
  private val prefix: String = "Hello"

  @silent("mutable field annotation")
  var suffix: String = "!"

  @silent("lazy value annotation")
  lazy val normalizedName: String = name.trim.toLowerCase(Locale.ROOT)

  @silent("method annotation")
  def greeting(@silent("method parameter annotation") punctuation: String): String = {
    s"$prefix, ${name.trim}$punctuation"
  }

  @silent("curried higher-order method annotation")
  def transform(value: Int)(operation: Int => Int): Int = {
    operation(value)
  }
}

@silent("companion object annotation")
object SilencerAnnotatedFixture {
  @silent("companion method annotation")
  def normalize(value: String): String = {
    value.toUpperCase(Locale.ROOT)
  }
}

@silent("trait annotation")
trait SilencerOperation[-A, +B] {
  @silent("abstract method annotation")
  def apply(@silent("abstract method parameter annotation") value: A): B
}

@silent("implementation class annotation")
final class IncrementOperation extends SilencerOperation[Int, Int] {
  @silent("overridden method annotation")
  override def apply(value: Int): Int = {
    value + 1
  }
}

@silent("case class annotation")
final case class SilencerResult[A](@silent("case class field annotation") label: String, value: A) {
  @silent("generic method annotation")
  def mapLabel(transform: String => String): SilencerResult[A] = {
    copy(label = transform(label))
  }

  @silent("computed property annotation")
  def describe: String = {
    s"$label:$value"
  }
}

final class SilencerTypeAliasFixture {
  @silent("simple type alias annotation")
  type Label = String

  @silent("nested generic type alias annotation")
  type Grouped[A] = Map[String, Vector[A]]

  def describe(label: Label, groupedValues: Grouped[Int]): String = {
    val total: Int = groupedValues.values.flatten.sum
    s"$label:$total"
  }
}

final class SilencerPipeline {
  @silent("higher-order collection method annotation")
  def parsePositiveIntegers(values: Iterable[String]): List[Int] = {
    values.flatMap(parse).filter(_ > 0).toList
  }

  @silent("option-returning method annotation")
  def summarize(values: List[Int]): Option[String] = {
    values match {
      case Nil => None
      case nonEmpty => Some(s"${nonEmpty.mkString(" + ")} = ${nonEmpty.sum}")
    }
  }

  @silent("private helper method annotation")
  private def parse(value: String): Option[Int] = {
    value.toIntOption
  }
}

@silent("implicit type class annotation")
trait SilencerFormatter[A] {
  def render(value: A): String
}

final class SilencerGenericBox[@silent("class type parameter annotation") A](val value: A) {
  @silent("method with annotated result type parameter")
  def map[@silent("map result type parameter annotation") B](transform: A => B): SilencerGenericBox[B] = {
    SilencerGenericBox(transform(value))
  }

  @silent("method with annotated peer type parameter")
  def zip[@silent("zip peer type parameter annotation") B](other: SilencerGenericBox[B]): SilencerGenericBox[(A, B)] = {
    SilencerGenericBox((value, other.value))
  }

  @silent("method with annotated folded type parameter")
  def fold[@silent("fold result type parameter annotation") B](combine: A => B): B = {
    combine(value)
  }
}

object SilencerGenericBox {
  def apply[@silent("factory type parameter annotation") A](value: A): SilencerGenericBox[A] = {
    new SilencerGenericBox(value)
  }
}

object SilencerImplicitFixture {
  @silent("implicit integer formatter annotation")
  implicit val intFormatter: SilencerFormatter[Int] = new SilencerFormatter[Int] {
    override def render(value: Int): String = {
      s"int:$value"
    }
  }

  @silent("implicit string formatter annotation")
  implicit val stringFormatter: SilencerFormatter[String] = new SilencerFormatter[String] {
    override def render(value: String): String = {
      s"string:$value"
    }
  }

  @silent("implicit option formatter annotation")
  implicit def optionFormatter[A](implicit formatter: SilencerFormatter[A]): SilencerFormatter[Option[A]] = {
    new SilencerFormatter[Option[A]] {
      override def render(value: Option[A]): String = {
        value match {
          case Some(innerValue) => s"some(${formatter.render(innerValue)})"
          case None => "none"
        }
      }
    }
  }

  @silent("implicit-parameter method annotation")
  def render[A](value: A)(implicit @silent("implicit formatter parameter annotation") formatter: SilencerFormatter[A]): String = {
    formatter.render(value)
  }

  @silent("implicit extension class annotation")
  implicit final class RenderOps[A](@silent("extension receiver annotation") private val value: A) extends AnyVal {
    @silent("extension method annotation")
    def rendered(implicit formatter: SilencerFormatter[A]): String = {
      render(value)
    }
  }
}

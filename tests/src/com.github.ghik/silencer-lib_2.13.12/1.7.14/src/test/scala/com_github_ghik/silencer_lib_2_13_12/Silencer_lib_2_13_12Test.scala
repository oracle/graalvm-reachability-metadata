/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ghik.silencer_lib_2_13_12

import com.github.ghik.silencer.SilencerPlugin
import com.github.ghik.silencer.silent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.io.File
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import scala.annotation.Annotation
import scala.jdk.CollectionConverters._
import scala.tools.nsc.Global
import scala.tools.nsc.Settings
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.reporters.StoreReporter

class Silencer_lib_2_13_12Test {
  @Test
  def constructorsCreateIndependentScalaAnnotationInstances(): Unit = {
    val withoutPattern: silent = new silent()
    val emptyPattern: silent = new silent("")
    val warningPattern: silent = new silent("(?s).*local val unusedValue.*never used.*")
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
    val result: CompilationResult = compileWithSilencer(
      "SilencedUnusedWarning.scala",
      """
        |package example
        |
        |import com.github.ghik.silencer.silent
        |
        |object SilencedUnusedWarning {
        |  @silent("(?s).*unusedSilencedLocal.*never used.*")
        |  def answer(): Int = {
        |    val unusedSilencedLocal: String = "compiled only when the warning is silenced"
        |    42
        |  }
        |}
        |""".stripMargin
    )

    assertTrue(result.success, result.renderedMessages)
    assertFalse(result.hasMessageContaining("unusedSilencedLocal"), result.renderedMessages)
  }

  @Test
  def compilerStillReportsUnsilencedUnusedLocalWarningsAsErrors(): Unit = {
    val result: CompilationResult = compileWithSilencer(
      "UnsilencedUnusedWarning.scala",
      """
        |package example
        |
        |object UnsilencedUnusedWarning {
        |  def answer(): Int = {
        |    val unsilencedLocal: String = "this local value should remain a fatal warning"
        |    42
        |  }
        |}
        |""".stripMargin
    )

    assertFalse(result.success, result.renderedMessages)
    assertTrue(result.hasMessageContaining("unsilencedLocal"), result.renderedMessages)
  }

  @Test
  def noArgumentSilentAnnotationSuppressesEveryWarningInAnnotatedScope(): Unit = {
    val result: CompilationResult = compileWithSilencer(
      "BroadlySilencedWarnings.scala",
      """
        |package example
        |
        |import com.github.ghik.silencer.silent
        |
        |object BroadlyDeprecatedApi {
        |  @deprecated("use replacement", "1.0")
        |  def oldValue: Int = 7
        |}
        |
        |object BroadlySilencedWarnings {
        |  @silent
        |  def value(): Int = {
        |    val unusedBroadlySilencedLocal: String = "covered by the no-argument silencer"
        |    BroadlyDeprecatedApi.oldValue
        |  }
        |}
        |""".stripMargin
    )

    assertTrue(result.success, result.renderedMessages)
    assertFalse(result.hasMessageContaining("unusedBroadlySilencedLocal"), result.renderedMessages)
    assertFalse(result.hasMessageContaining("oldValue"), result.renderedMessages)
  }

  @Test
  def silentAnnotationSuppressesDeprecationWarningMatchedByMessagePattern(): Unit = {
    val result: CompilationResult = compileWithSilencer(
      "SilencedDeprecationWarning.scala",
      """
        |package example
        |
        |import com.github.ghik.silencer.silent
        |
        |object DeprecatedApi {
        |  @deprecated("use replacement", "1.0")
        |  def oldValue: Int = 7
        |
        |  def replacement: Int = 8
        |}
        |
        |object SilencedDeprecationWarning {
        |  @silent("(?s).*oldValue.*deprecated.*")
        |  def value(): Int = DeprecatedApi.oldValue + DeprecatedApi.replacement
        |}
        |""".stripMargin
    )

    assertTrue(result.success, result.renderedMessages)
    assertFalse(result.hasMessageContaining("oldValue"), result.renderedMessages)
  }

  @Test
  def messagePatternOnlySuppressesMatchingCompilerWarnings(): Unit = {
    val result: CompilationResult = compileWithSilencer(
      "PartiallySilencedWarnings.scala",
      """
        |package example
        |
        |import com.github.ghik.silencer.silent
        |
        |object PartiallyDeprecatedApi {
        |  @deprecated("use replacement", "1.0")
        |  def oldValue: Int = 7
        |}
        |
        |object PartiallySilencedWarnings {
        |  @silent("(?s).*oldValue.*deprecated.*")
        |  def value(): Int = {
        |    val stillUnsilencedLocal: String = "not matched by the deprecation-only silencer"
        |    PartiallyDeprecatedApi.oldValue
        |  }
        |}
        |""".stripMargin
    )

    assertFalse(result.success, result.renderedMessages)
    assertTrue(result.hasMessageContaining("stillUnsilencedLocal"), result.renderedMessages)
  }

  @Test
  def annotatedDefinitionsKeepNormalScalaRuntimeSemantics(): Unit = {
    val fixture: SilencerAnnotatedFixture = new SilencerAnnotatedFixture(" Native Image ")

    assertEquals("Hello, Native Image!", fixture.greeting("!"))
    assertEquals("native image", fixture.normalizedName)
    assertEquals(42, fixture.transform(41)(_ + 1))
    assertEquals("NATIVE IMAGE", SilencerAnnotatedFixture.normalize("native image"))

    fixture.suffix = "?"
    assertEquals("Hello, Native Image?", fixture.greeting(fixture.suffix))
  }

  @Test
  def localExpressionTypeAliasAndGenericAnnotationsDoNotChangeEvaluation(): Unit = {
    @silent("local collection used to verify expression evaluation")
    val baseValues: Vector[Int] = Vector(2, 4, 8)

    val folded: Int = ({
      @silent("local accumulator seed")
      val seed: Int = 1
      baseValues.foldLeft(seed)(_ + _)
    }: @silent("block expression annotation"))
    val rendered: String = (baseValues.map(_ / 2).mkString("-"): @silent("method call expression annotation"))

    val aliases: SilencerTypeAliasFixture = new SilencerTypeAliasFixture()
    val label: aliases.Label = "metadata"
    val groupedValues: aliases.Grouped[Int] = Map("odd" -> Vector(1, 3), "even" -> Vector(2, 4))

    val first: SilencerGenericBox[String] = SilencerGenericBox("metadata")
    val length: SilencerGenericBox[Int] = first.map(_.length)

    assertEquals(15, folded)
    assertEquals("1-2-4", rendered)
    assertEquals("metadata:10", aliases.describe(label, groupedValues))
    assertEquals(("metadata", 8), first.zip(length).value)
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

  private def compileWithSilencer(fileName: String, source: String): CompilationResult = {
    val tempDirectory: Path = Files.createTempDirectory("silencer-compiler-test-")
    try {
      val sourceFile: Path = tempDirectory.resolve(fileName)
      val classesDirectory: Path = Files.createDirectories(tempDirectory.resolve("classes"))
      Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8))

      val reporter: StoreReporter = new StoreReporter()
      val settings: Settings = compilerSettings(classesDirectory)
      val global: Global = new Global(settings, reporter) {
        override protected def loadRoughPluginsList(): List[Plugin] = {
          List(new SilencerPlugin(this))
        }
      }
      val run: global.Run = new global.Run()
      run.compile(List(sourceFile.toString))

      CompilationResult(!reporter.hasErrors, reporter.infos.toList.map(_.msg))
    } finally {
      deleteRecursively(tempDirectory)
    }
  }

  private def compilerSettings(classesDirectory: Path): Settings = {
    val settings: Settings = new Settings(message => throw new IllegalArgumentException(message))
    settings.usejavacp.value = true
    settings.outputDirs.setSingleOutput(classesDirectory.toString)
    settings.classpath.value = runtimeClasspath

    val arguments: List[String] = List(
      "-encoding",
      StandardCharsets.UTF_8.name(),
      "-deprecation",
      "-Wunused:locals",
      "-Werror"
    )
    val (processed, unprocessed): (Boolean, List[String]) = settings.processArguments(arguments, processAll = true)
    if (!processed || unprocessed.nonEmpty) {
      throw new IllegalArgumentException(s"Could not configure scalac options: ${unprocessed.mkString(" ")}")
    }
    settings
  }

  private def runtimeClasspath: String = {
    val propertyEntries: List[String] = System.getProperty("java.class.path", "")
      .split(File.pathSeparator)
      .filter(_.nonEmpty)
      .toList
    val loaderEntries: List[String] = classLoaderHierarchy(Thread.currentThread().getContextClassLoader).flatMap {
      case loader: URLClassLoader => loader.getURLs.toList.collect {
          case url if url.getProtocol == "file" => Path.of(url.toURI).toString
        }
      case _ => Nil
    }

    (propertyEntries ++ loaderEntries).distinct.mkString(File.pathSeparator)
  }

  private def classLoaderHierarchy(classLoader: ClassLoader): List[ClassLoader] = {
    if (classLoader == null) {
      Nil
    } else {
      classLoader :: classLoaderHierarchy(classLoader.getParent)
    }
  }

  private def deleteRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      val paths = Files.walk(path)
      try {
        paths.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach { current: Path =>
          Files.deleteIfExists(current)
        }
      } finally {
        paths.close()
      }
    }
  }
}

final case class CompilationResult(success: Boolean, messages: List[String]) {
  def hasMessageContaining(text: String): Boolean = {
    messages.exists(_.contains(text))
  }

  def renderedMessages: String = {
    if (messages.isEmpty) {
      "No compiler diagnostics were reported."
    } else {
      messages.mkString(System.lineSeparator())
    }
  }
}

@silent("class-level silencer annotation")
final class SilencerAnnotatedFixture(@silent("constructor parameter annotation") val name: String) {
  @silent("field annotation")
  private val prefix: String = "Hello"

  @silent("mutable field annotation")
  var suffix: String = "!"

  @silent("lazy value annotation")
  lazy val normalizedName: String = name.trim.toLowerCase(java.util.Locale.ROOT)

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
    value.toUpperCase(java.util.Locale.ROOT)
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

final class SilencerGenericBox[@silent("class type parameter annotation") A](val value: A) {
  @silent("method with annotated result type parameter")
  def map[@silent("map result type parameter annotation") B](transform: A => B): SilencerGenericBox[B] = {
    SilencerGenericBox(transform(value))
  }

  @silent("method with annotated peer type parameter")
  def zip[@silent("zip peer type parameter annotation") B](other: SilencerGenericBox[B]): SilencerGenericBox[(A, B)] = {
    SilencerGenericBox((value, other.value))
  }
}

object SilencerGenericBox {
  def apply[@silent("factory type parameter annotation") A](value: A): SilencerGenericBox[A] = {
    new SilencerGenericBox(value)
  }
}

@silent("implicit type class annotation")
trait SilencerFormatter[A] {
  def render(value: A): String
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
  def render[A](value: A)(
      implicit @silent("implicit formatter parameter annotation") formatter: SilencerFormatter[A]
  ): String = {
    formatter.render(value)
  }

  @silent("implicit extension class annotation")
  implicit final class RenderOps[A](
      @silent("extension receiver annotation") private val value: A
  ) extends AnyVal {
    @silent("extension method annotation")
    def rendered(implicit formatter: SilencerFormatter[A]): String = {
      render(value)
    }
  }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.literally_3

import dotty.tools.dotc.Driver
import java.nio.file.{Files, Path}
import java.util.Locale
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test
import org.typelevel.literally.Literally
import scala.jdk.CollectionConverters.*
import scala.quoted.{Expr, Quotes}

class Literally_3Test {
  @Test
  def validIntegerLiteralInterpolatorCompilesThroughLiterallyMacro(): Unit = {
    compileSnippetWithNativeFallback(
      """import org_typelevel.literally_3.Literally_3TestSyntax.*
        |
        |object ValidIntegerLiteral {
        |  val singleDigit: Int = positiveInt"7"
        |  val answer: Int = positiveInt"40" + positiveInt"2"
        |  val maximum: Int = positiveInt"2147483647"
        |}
        |""".stripMargin
    ).foreach { result =>
      assertThat(result.hasErrors).isFalse
      assertThat(result.messages).isEmpty
    }
  }

  @Test
  def independentLiteralImplementationsCompileInTheSameUserProgram(): Unit = {
    compileSnippetWithNativeFallback(
      """import org_typelevel.literally_3.Literally_3TestSyntax.*
        |
        |object MultipleLiteralImplementations {
        |  val releaseToken: String = normalizedToken"Release_Candidate-1"
        |  val mapping: Map[String, Int] = Map(
        |    normalizedToken"Primary_Node" -> positiveInt"1",
        |    normalizedToken"Backup_Node" -> positiveInt"2"
        |  )
        |}
        |""".stripMargin
    ).foreach { result =>
      assertThat(result.hasErrors).isFalse
      assertThat(result.messages).isEmpty
    }
  }

  @Test
  def validationFailuresAreReportedAsCompileTimeErrors(): Unit = {
    compileSnippetWithNativeFallback(
      """import org_typelevel.literally_3.Literally_3TestSyntax.*
        |
        |object InvalidIntegerLiteral {
        |  val value: Int = positiveInt"0"
        |}
        |""".stripMargin
    ).foreach { result =>
      assertThat(result.hasErrors).isTrue
      assertThat(result.messages).contains("Expected a positive base-10 integer literal, got: 0")
    }

    compileSnippetWithNativeFallback(
      """import org_typelevel.literally_3.Literally_3TestSyntax.*
        |
        |object InvalidTokenLiteral {
        |  val value: String = normalizedToken"contains spaces"
        |}
        |""".stripMargin
    ).foreach { result =>
      assertThat(result.hasErrors).isTrue
      assertThat(result.messages).contains("Expected an ASCII token literal, got: contains spaces")
    }
  }

  @Test
  def interpolationArgumentsAreRejectedByTheDefaultLiteralApplyMethod(): Unit = {
    compileSnippetWithNativeFallback(
      """import org_typelevel.literally_3.Literally_3TestSyntax.*
        |
        |object InterpolatedLiteral {
        |  val suffix: Int = 3
        |  val value: Int = positiveInt"12$suffix"
        |}
        |""".stripMargin
    ).foreach { result =>
      assertThat(result.hasErrors).isTrue
      assertThat(result.messages).contains("interpolation not supported")
    }
  }

  @Test
  def literalInstancesAreOrdinaryRuntimeValues(): Unit = {
    import Literally_3TestSyntax.*

    assertThat(PositiveInt).isNotNull
    assertThat(PositiveInt.Expr).isNotNull
    assertThat(NormalizedToken).isNotNull
    assertThat(NormalizedToken.Expr).isNotNull
  }

  private def compileSnippetWithNativeFallback(source: String): Option[CompilationResult] =
    try Some(compileSnippet(source))
    catch {
      case error: Error =>
        if NativeImageSupport.isUnsupportedFeatureError(error) then None
        else throw error
    }

  private def compileSnippet(source: String): CompilationResult = {
    val temporaryDirectory: Path = Files.createTempDirectory("literally-test-")
    try {
      val outputDirectory: Path = Files.createDirectories(temporaryDirectory.resolve("classes"))
      val sourceFile: Path = temporaryDirectory.resolve("Snippet.scala")
      Files.writeString(sourceFile, source)

      val reporter = new Driver().process(Array(
        "-classpath",
        System.getProperty("java.class.path"),
        "-d",
        outputDirectory.toString,
        sourceFile.toString
      ))
      val messages: String = reporter.allErrors.map(_.message()).mkString("\n")
      CompilationResult(reporter.hasErrors(), messages)
    } finally {
      deleteRecursively(temporaryDirectory)
    }
  }

  private def deleteRecursively(path: Path): Unit = {
    if Files.isDirectory(path) then {
      val children = Files.list(path)
      try children.iterator().asScala.foreach(deleteRecursively)
      finally children.close()
    }
    Files.deleteIfExists(path)
  }

  private case class CompilationResult(hasErrors: Boolean, messages: String)
}

object Literally_3TestSyntax {
  object PositiveInt extends Literally[Int] {
    override def validate(s: String)(using Quotes): Either[String, Expr[Int]] =
      s.toIntOption match {
        case Some(value) if value > 0 => Right(Expr(value))
        case _ => Left(s"Expected a positive base-10 integer literal, got: $s")
      }
  }

  object NormalizedToken extends Literally[String] {
    override def validate(s: String)(using Quotes): Either[String, Expr[String]] =
      if s.matches("[A-Za-z][A-Za-z0-9_-]*") then Right(Expr(s.toLowerCase(Locale.ROOT)))
      else Left(s"Expected an ASCII token literal, got: $s")
  }

  extension (inline context: StringContext) {
    inline def positiveInt(inline args: Any*): Int = ${ PositiveInt('context, 'args) }

    inline def normalizedToken(inline args: Any*): String = ${ NormalizedToken('context, 'args) }
  }
}

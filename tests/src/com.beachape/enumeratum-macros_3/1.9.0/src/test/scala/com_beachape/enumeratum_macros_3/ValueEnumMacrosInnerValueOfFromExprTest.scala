/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beachape.enumeratum_macros_3

import dotty.tools.dotc.Driver
import dotty.tools.dotc.reporting.Reporter
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import scala.quoted.Expr
import scala.quoted.Quotes
import scala.quoted.Type

class ValueEnumMacrosInnerValueOfFromExprTest {
  @Test
  def runtimeCompilationExercisesValueOfFromExprForPrecompiledSingletons(): Unit = {
    try {
      val workDir: Path = Files.createTempDirectory("enumeratum-value-of-from-expr")
      val outputDir: Path = Files.createDirectories(workDir.resolve("classes"))
      val source: Path = workDir.resolve("RuntimeValueOfFromExprProbe.scala")
      Files.writeString(
        source,
        """
          |package com_beachape.enumeratum_macros_3.runtime_compilation
          |
          |import com_beachape.enumeratum_macros_3.ValueOfFromExprProbe
          |
          |object RuntimeValueOfFromExprProbe {
          |  val reached: Boolean = ValueOfFromExprProbe.reachesReflection
          |}
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val arguments: Array[String] = Array(
        "-classpath",
        System.getProperty("java.class.path"),
        "-d",
        outputDir.toString,
        "-Yretain-trees",
        source.toString
      )
      val reporter: Reporter = Driver().process(arguments)

      assertFalse(reporter.hasErrors(), reporter.summary)
    } catch {
      case error: Error =>
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
          throw error
        }
    }
  }
}

object ValueOfFromExprProbe {
  inline def reachesReflection: Boolean = ${ reachesReflectionImpl }

  private def reachesReflectionImpl(using quotes: Quotes): Expr[Boolean] = {
    import quotes.reflect.report

    val valueOfExpr: Expr[ValueOf[LowPriority.type]] =
      '{ new ValueOf[LowPriority.type](LowPriority) }
    val extractorClass = Class.forName("enumeratum.ValueEnumMacros$ValueOfFromExpr")
    val constructor = extractorClass.getDeclaredConstructor(classOf[Type[?]])
    constructor.setAccessible(true)
    val extractor = constructor.newInstance(Type.of[LowPriority.type])
    val unapply = extractorClass.getDeclaredMethod(
      "unapply",
      classOf[Expr[?]],
      classOf[Quotes]
    )
    unapply.setAccessible(true)
    val extracted = unapply
      .invoke(extractor, valueOfExpr, quotes)
      .asInstanceOf[Option[ValueOf[LowPriority.type]]]

    if (extracted.exists(_.value == LowPriority)) {
      Expr(true)
    } else {
      report.errorAndAbort("ValueOfFromExpr did not extract the singleton module")
    }
  }
}

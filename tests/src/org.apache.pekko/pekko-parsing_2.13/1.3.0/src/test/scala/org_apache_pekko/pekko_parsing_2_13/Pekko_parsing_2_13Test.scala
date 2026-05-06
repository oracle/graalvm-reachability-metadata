/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_parsing_2_13

import org.apache.pekko.http.ccompat.{pre213macro, since213macro}
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.tools.nsc.{Global, Settings}
import scala.tools.nsc.reporters.StoreReporter

class PekkoParsingCompatibilityAnnotationsTest {
  @Test
  def exposesMacroImplementationCompanions(): Unit = {
    assertThat(since213macro).isNotNull
    assertThat(pre213macro).isNotNull
  }

  @Test
  def since213AnnotationKeepsAnnotatedMemberOnScala213(): Unit = withUnsupportedFeatureHandling {
    val reporter: StoreReporter = compileScalaSource(
      "CompatibilitySample.scala",
      """
        |import org.apache.pekko.http.ccompat.{pre213, since213}
        |
        |object CompatibilitySample {
        |  @since213 def since213Only: String = "available on Scala 2.13"
        |  @pre213 def pre213Only: String = "removed on Scala 2.13"
        |}
        |
        |object CompatibilityProbe {
        |  val selected: String = CompatibilitySample.since213Only
        |}
        |""".stripMargin
    )

    assertThat(reporter.hasErrors).isFalse
  }

  @Test
  def pre213AnnotationRemovesAnnotatedMemberOnScala213(): Unit = withUnsupportedFeatureHandling {
    val reporter: StoreReporter = compileScalaSource(
      "CompatibilitySample.scala",
      """
        |import org.apache.pekko.http.ccompat.{pre213, since213}
        |
        |object CompatibilitySample {
        |  @since213 def since213Only: String = "available on Scala 2.13"
        |  @pre213 def pre213Only: String = "removed on Scala 2.13"
        |}
        |
        |object CompatibilityProbe {
        |  val removed: String = CompatibilitySample.pre213Only
        |}
        |""".stripMargin
    )

    assertThat(reporter.hasErrors).isTrue
    assertThat(reporter.infos.exists { info: StoreReporter#Info =>
      info.msg.contains("pre213Only") && info.msg.contains("is not a member")
    }).isTrue
  }

  private def compileScalaSource(fileName: String, source: String): StoreReporter = {
    val directory: Path = Files.createTempDirectory("pekko-parsing-macro-test")
    val sourceFile: Path = directory.resolve(fileName)
    val outputDirectory: Path = directory.resolve("classes")
    Files.createDirectories(outputDirectory)
    Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8))

    val settings: Settings = new Settings
    val arguments: List[String] = List(
      "-Ymacro-annotations",
      "-classpath",
      System.getProperty("java.class.path"),
      "-d",
      outputDirectory.toString
    )
    settings.processArguments(arguments, processAll = true)

    val reporter: StoreReporter = new StoreReporter
    val compiler: Global = new Global(settings, reporter)
    val run: compiler.Run = new compiler.Run
    run.compile(List(sourceFile.toString))
    reporter
  }

  private def withUnsupportedFeatureHandling(testBody: => Unit): Unit = {
    try {
      testBody
    } catch {
      case error: Error =>
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
          throw error
        }
    }
  }
}

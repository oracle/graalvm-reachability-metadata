/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_macros_3

import dotty.tools.dotc.Main
import dotty.tools.dotc.reporting.Reporter
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

class CompileTimeEvalInnerQuoteScopeInnerDirectJvmReflectionMethodCallTest {
  @Test
  def compilesAdtCodecWithClassNameMapperCallingRuntimeModuleMethod(): Unit = {
    try {
      val workDir: Path = Files.createTempDirectory("jsoniter-scala-direct-jvm-reflection")
      try compileCodec(workDir)
      finally deleteRecursively(workDir)
    } catch {
      case error: Error =>
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
          throw error
        }
    }
  }

  private def compileCodec(workDir: Path): Unit = {
    val sourceDir: Path = Files.createDirectories(workDir.resolve("src"))
    val classesDir: Path = Files.createDirectories(workDir.resolve("classes"))
    val source: Path = sourceDir.resolve("DirectJvmReflectionCodecTarget.scala")

    writeSource(
      source,
      """
        |import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
        |import com.github.plokhotnyuk.jsoniter_scala.macros.CodecMakerConfig
        |import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
        |import com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_macros_3.RuntimeDirectJvmReflectionMapperTest
        |
        |sealed trait DirectJvmReflectionEvent
        |final case class DirectJvmReflectionFirst(value: String) extends DirectJvmReflectionEvent
        |final case class DirectJvmReflectionSecond(value: Int) extends DirectJvmReflectionEvent
        |
        |object DirectJvmReflectionCodecTarget {
        |  val codec: JsonValueCodec[DirectJvmReflectionEvent] =
        |    JsonCodecMaker.make[DirectJvmReflectionEvent](
        |      CodecMakerConfig.withAdtLeafClassNameMapper { className =>
        |        RuntimeDirectJvmReflectionMapperTest.rename(className)
        |      }
        |    )
        |}
        |""".stripMargin
    )
    compileSources(Seq(source), classesDir, System.getProperty("java.class.path"))
  }

  private def compileSources(sources: Seq[Path], outputDir: Path, classpath: String): Unit = {
    val args: Array[String] = Array("-classpath", classpath, "-d", outputDir.toString) ++ sources.map(_.toString)
    val reporter: Reporter = Main.process(args)
    if (reporter.hasErrors) {
      fail("Expected Scala compilation to succeed, but it reported errors:\n" + reporter.allErrors.mkString("\n"))
    }
  }

  private def writeSource(path: Path, source: String): Unit = {
    Files.write(path, source.getBytes(UTF_8))
  }

  private def deleteRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream: java.util.stream.Stream[Path] = Files.walk(path)
      try {
        val iterator: java.util.Iterator[Path] = stream.sorted(Comparator.reverseOrder[Path]()).iterator()
        while (iterator.hasNext) {
          Files.deleteIfExists(iterator.next())
          ()
        }
      } finally {
        stream.close()
      }
    }
  }
}

object RuntimeDirectJvmReflectionMapperTest {
  def rename(className: String): String = {
    if (className.endsWith("DirectJvmReflectionFirst")) "first"
    else if (className.endsWith("DirectJvmReflectionSecond")) "second"
    else className
  }
}

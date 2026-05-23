/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_macros_3

import dotty.tools.dotc.Main
import dotty.tools.dotc.config.Properties
import dotty.tools.dotc.reporting.Reporter
import dotty.tools.io.JDK9Reflectors
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipFile

class CompileTimeEvalInnerQuoteScopeInnerDirectJvmReflectionMethodCallTest {
  @Test
  def compilesPlainScalaSourceAndUsesCompilerRuntimeHelpers(): Unit = {
    try {
      val workDir: Path = Files.createTempDirectory("jsoniter-scala-compiler-smoke")
      try runCompilerSmokeScenario(workDir)
      finally deleteRecursively(workDir)
    } catch {
      case error: Error =>
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
          throw error
        }
    }
  }

  private def runCompilerSmokeScenario(workDir: Path): Unit = {
    val versionMessage: String = Properties.versionMsg
    assertNotNull(versionMessage)
    assertTrue(versionMessage != "")

    val versionNumber: String = Properties.scalaPropOrEmpty("version.number")
    assertNotNull(versionNumber)
    assertTrue(versionNumber != "")

    val runtimeVersion: Object = JDK9Reflectors.runtimeVersion()
    assertNotNull(runtimeVersion)
    val runtimeMajor: Integer = JDK9Reflectors.runtimeVersionMajor(runtimeVersion)
    assertTrue(runtimeMajor.intValue() >= 9)

    val jarPath: Path = createJarFile(workDir.resolve("compiler-smoke.jar"))
    val jarFile: java.util.jar.JarFile =
      JDK9Reflectors.newJarFile(jarPath.toFile, false, ZipFile.OPEN_READ, runtimeVersion)
    try {
      assertNotNull(jarFile.getManifest)
      assertNotNull(jarFile.getEntry("META-INF/MANIFEST.MF"))
    } finally {
      jarFile.close()
    }

    compileCompilerSmokeSource(workDir)
  }

  private def compileCompilerSmokeSource(workDir: Path): Unit = {
    val sourceDir: Path = Files.createDirectories(workDir.resolve("src"))
    val classesDir: Path = Files.createDirectories(workDir.resolve("classes"))
    val source: Path = sourceDir.resolve("CompilerMetadataSmoke.scala")
    writeSource(
      source,
      """
        |import com.github.plokhotnyuk.jsoniter_scala.macros.CodecMakerConfig
        |
        |final class CompilerMetadataSmoke(val name: String, val value: Int)
        |
        |object CompilerMetadataSmokeMain {
        |  def config: CodecMakerConfig = CodecMakerConfig.withRequireCollectionFields(true)
        |
        |  def smokeValue: Int = new CompilerMetadataSmoke("native-image", 41).value
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

  private def createJarFile(path: Path): Path = {
    val manifest: Manifest = new Manifest()
    manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
    val outputStream: JarOutputStream = new JarOutputStream(Files.newOutputStream(path), manifest)
    try {
      ()
    } finally {
      outputStream.close()
    }
    path
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

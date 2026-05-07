/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.common_2_13

import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import scala.jdk.CollectionConverters._
import scala.reflect.internal.util.BatchSourceFile
import scala.tools.nsc.Global
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.StoreReporter

class RegistryMacrosAnonymous1Test {
  @Test
  def expandsRegistryMacroDuringRuntimeCompilation(): Unit = {
    try {
      compileRegistryAnnotatedModule()
    } catch {
      case error: Error =>
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
          throw error
        }
    }
  }

  private def compileRegistryAnnotatedModule(): Unit = {
    val outputDirectory: Path = Files.createTempDirectory("scalameta-registry-macro-test")
    try {
      val reporter: StoreReporter = new StoreReporter()
      val compiler: Global = new Global(compilerSettings(outputDirectory), reporter)
      val sourceFile: BatchSourceFile = new BatchSourceFile(
        "RuntimeRegistryMacroFixture.scala",
        registryAnnotatedModuleSource
      )

      new compiler.Run().compileSources(List(sourceFile))

      assertThat(reporter.hasErrors).isFalse()
    } finally {
      deleteRecursively(outputDirectory)
    }
  }

  private def compilerSettings(outputDirectory: Path): Settings = {
    val settings: Settings = new Settings()
    val processed: (Boolean, List[String]) = settings.processArguments(
      List(
        "-usejavacp",
        "-Ymacro-annotations",
        "-classpath",
        runtimeClasspath,
        "-d",
        outputDirectory.toString
      ),
      processAll = true
    )
    assertThat(processed._1).isTrue()
    assertThat(processed._2.asJava).isEmpty()
    settings
  }

  private def runtimeClasspath: String = {
    val propertyClasspath: Seq[String] = System
      .getProperty("java.class.path", "")
      .split(System.getProperty("path.separator"))
      .toSeq
    val loaderClasspath: Seq[String] = classLoaderClasspath(getClass.getClassLoader)
    (propertyClasspath ++ loaderClasspath).filter(_.nonEmpty).distinct.mkString(System.getProperty("path.separator"))
  }

  private def classLoaderClasspath(classLoader: ClassLoader): Seq[String] = {
    classLoader match {
      case null => Seq.empty
      case urlClassLoader: URLClassLoader =>
        urlClassLoader.getURLs.toSeq.map(url => Paths.get(url.toURI).toString) ++
          classLoaderClasspath(classLoader.getParent)
      case _ => classLoaderClasspath(classLoader.getParent)
    }
  }

  private def deleteRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream: java.util.stream.Stream[Path] = Files.walk(path)
      try {
        stream.iterator().asScala.toSeq.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }
  }

  private def registryAnnotatedModuleSource: String = {
    """
      |package org_scalameta.common_2_13.generated
      |
      |import scala.meta.internal.trees.registry
      |
      |@registry
      |object RuntimeRegistryMacroFixture {
      |  val marker: String = "runtime registry macro expanded"
      |}
      |""".stripMargin
  }
}

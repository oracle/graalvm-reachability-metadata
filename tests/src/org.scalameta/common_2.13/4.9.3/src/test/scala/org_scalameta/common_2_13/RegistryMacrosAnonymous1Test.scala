/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.common_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import scala.jdk.CollectionConverters._
import scala.reflect.internal.util.BatchSourceFile
import scala.tools.nsc.Global
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.StoreReporter

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class RegistryMacrosAnonymous1Test {
  @Test
  def registryMacroAnnotatesObjectsDuringCompilation(): Unit = {
    val outputDirectory: Path = Files.createTempDirectory("scalameta-registry-macro")
    try {
      val reporter: StoreReporter = compileRegistryAnnotatedObject(outputDirectory)

      assertFalse(reporter.hasErrors, renderMessages(reporter))
    } finally {
      deleteRecursively(outputDirectory)
    }
  }

  private def compileRegistryAnnotatedObject(outputDirectory: Path): StoreReporter = {
    val settings: Settings = new Settings
    val classpath: String = compilerClasspath
    val arguments: List[String] = List(
      "-Ymacro-annotations",
      "-Ystop-after:typer",
      "-classpath",
      classpath,
      "-d",
      outputDirectory.toString
    )
    val (argumentsAccepted, argumentErrors) = settings.processArguments(arguments, processAll = true)
    assertThat(argumentsAccepted).isTrue()
    assertThat(argumentErrors.asJava).isEmpty()

    val reporter: StoreReporter = new StoreReporter(settings)
    val compiler: Global = new Global(settings, reporter)
    val run: compiler.Run = new compiler.Run
    run.compileSources(List(new BatchSourceFile("RegistryMacroFixture.scala", registryFixtureSource)))
    reporter
  }

  private def compilerClasspath: String = {
    val codeSourceEntries: List[String] = List(
      classOf[scala.meta.internal.trees.Metadata.Ast],
      classOf[Global],
      classOf[scala.reflect.api.Universe],
      classOf[Option[_]]
    ).flatMap(codeSourcePath)
    val classLoaderEntries: List[String] = classLoaderClasspath(
      Thread.currentThread().getContextClassLoader
    )
    val propertyEntries: List[String] = System
      .getProperty("java.class.path", "")
      .split(File.pathSeparator)
      .filter(_.nonEmpty)
      .toList

    (codeSourceEntries ++ classLoaderEntries ++ propertyEntries).distinct.mkString(File.pathSeparator)
  }

  private def codeSourcePath(clazz: Class[_]): Option[String] =
    Option(clazz.getProtectionDomain)
      .flatMap(domain => Option(domain.getCodeSource))
      .map(source => Paths.get(source.getLocation.toURI).toString)

  private def classLoaderClasspath(classLoader: ClassLoader): List[String] =
    classLoader match {
      case null => Nil
      case urlClassLoader: URLClassLoader =>
        urlClassLoader.getURLs.map(url => Paths.get(url.toURI).toString).toList ++
          classLoaderClasspath(urlClassLoader.getParent)
      case other => classLoaderClasspath(other.getParent)
    }

  private def registryFixtureSource: String =
    """
      |package example
      |
      |import scala.meta.internal.trees.registry
      |
      |@registry
      |object RegistryMacroFixture
      |""".stripMargin

  private def renderMessages(reporter: StoreReporter): String =
    reporter.infos
      .iterator
      .map(info => s"${info.severity}: ${info.msg}")
      .mkString("\n")

  private def deleteRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      val paths: java.util.stream.Stream[Path] = Files.walk(path)
      try {
        paths.iterator().asScala.toSeq.reverse.foreach((file: Path) => Files.deleteIfExists(file))
      } finally {
        paths.close()
      }
    }
  }
}

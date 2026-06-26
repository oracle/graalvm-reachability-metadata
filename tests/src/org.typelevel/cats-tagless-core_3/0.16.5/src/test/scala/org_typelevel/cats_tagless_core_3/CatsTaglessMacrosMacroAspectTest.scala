/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_tagless_core_3

import cats.tagless.Derive
import dotty.tools.dotc.Main
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile
import scala.annotation.experimental
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@experimental
class CatsTaglessMacrosMacroAspectTest {
  @TempDir
  var tempDir: Path = uninitialized

  @Test
  def scalaCompilerExpandsAspectForMethodsWithGivenEvidence(): Unit = {
    val source: Path = tempDir.resolve("RuntimeMacroAspectProbe.scala")
    Files.writeString(source, runtimeMacroAspectProbeSource, StandardCharsets.UTF_8)

    val classes: Path = tempDir.resolve("classes")
    Files.createDirectories(classes)

    val args: Array[String] = Array(
      "-classpath",
      compilerClasspath,
      "-d",
      classes.toString,
      source.toString
    )

    try {
      val reporter = Main.process(args)
      if reporter.hasErrors then throw new AssertionError(reporter.allErrors.mkString(System.lineSeparator()))
    } catch {
      case error: Error =>
        if !NativeImageSupport.isUnsupportedFeatureError(error) then throw error
    }
  }

  private def runtimeMacroAspectProbeSource: String =
    """
      |package org_typelevel.cats_tagless_core_3.runtime_aspect_probe
      |
      |import cats.Id
      |import cats.tagless.Derive
      |import cats.tagless.aop.Aspect
      |import scala.annotation.experimental
      |
      |trait Evidence[A]:
      |  def label: String
      |
      |trait RuntimeAspectAlgebra[F[_]]:
      |  def describe(value: Int)(using Evidence[String]): F[String]
      |
      |@experimental
      |object RuntimeMacroAspectProbe:
      |  given Evidence[Int] with
      |    def label: String = "integer-argument"
      |
      |  given Evidence[String] with
      |    def label: String = "string-result"
      |
      |  val algebra: RuntimeAspectAlgebra[Id] = new RuntimeAspectAlgebra[Id]:
      |    def describe(value: Int)(using Evidence[String]): String = s"value-$value"
      |
      |  val aspect: Aspect[RuntimeAspectAlgebra, Evidence, Evidence] =
      |    Derive.aspect[RuntimeAspectAlgebra, Evidence, Evidence]
      |
      |  val woven: RuntimeAspectAlgebra[[X] =>> Aspect.Weave[Id, Evidence, Evidence, X]] =
      |    aspect.weave(algebra)
      |
      |  val result: Aspect.Weave[Id, Evidence, Evidence, String] =
      |    woven.describe(7)(using new Evidence[String]:
      |      def label: String = "method-result"
      |    )
      |""".stripMargin

  private def compilerClasspath: String = {
    val effectiveDirectory: Path = instrumentedCatsTaglessDirectory
    val entries: List[String] = List(
      catsTaglessCompilerClasspathEntries.map(_.toString),
      classpathPropertyEntries,
      contextClassLoaderEntries,
      knownClassLocations.map(_.toString),
      gradleCacheJars.map(_.toString)
    ).flatten.distinct.filterNot(entry => Paths.get(entry).toAbsolutePath.normalize == effectiveDirectory.toAbsolutePath.normalize)

    entries.mkString(java.io.File.pathSeparator)
  }

  private def instrumentedCatsTaglessDirectory: Path = Paths.get(
    "build",
    "jacoco",
    "effective",
    "cats_tagless_core_3_0_16_5_jar"
  )

  private def catsTaglessCompilerClasspathEntries: List[Path] = {
    val effectiveDirectory: Path = instrumentedCatsTaglessDirectory
    catsTaglessOriginalJar match {
      case Some(originalJar) if Files.isDirectory(effectiveDirectory) =>
        val mergedDirectory: Path = tempDir.resolve("cats-tagless-instrumented-with-tasty")
        copyDirectory(effectiveDirectory, mergedDirectory)
        copyTastyFiles(originalJar, mergedDirectory)
        List(mergedDirectory)
      case Some(originalJar) => List(originalJar)
      case None => Option.when(Files.isDirectory(effectiveDirectory))(effectiveDirectory).toList
    }
  }

  private def catsTaglessOriginalJar: Option[Path] =
    List(classpathJar("cats-tagless-core_3"), cachedJar("org.typelevel", "cats-tagless-core_3")).flatten.headOption

  private def classpathJar(artifact: String): Option[Path] = {
    val entries: List[String] = classpathPropertyEntries ++ contextClassLoaderEntries
    entries
      .map(Paths.get(_))
      .filter(path => Files.isRegularFile(path))
      .filter(path => path.getFileName.toString.startsWith(s"$artifact-"))
      .filter(path => path.getFileName.toString.endsWith(".jar"))
      .filterNot(path => path.getFileName.toString.endsWith("-sources.jar"))
      .filterNot(path => path.getFileName.toString.endsWith("-javadoc.jar"))
      .headOption
  }

  private def copyDirectory(source: Path, target: Path): Unit = {
    val stream = Files.walk(source)
    try {
      stream.iterator().asScala.foreach { path =>
        val relativePath: Path = source.relativize(path)
        val targetPath: Path = target.resolve(relativePath)
        if Files.isDirectory(path) then Files.createDirectories(targetPath)
        else {
          Files.createDirectories(targetPath.getParent)
          Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING)
        }
      }
    } finally stream.close()
  }

  private def copyTastyFiles(sourceJar: Path, target: Path): Unit = {
    val jarFile = new JarFile(sourceJar.toFile)
    try {
      jarFile
        .entries()
        .asScala
        .filter(entry => !entry.isDirectory && entry.getName.endsWith(".tasty"))
        .foreach { entry =>
          val targetPath: Path = target.resolve(entry.getName)
          Files.createDirectories(targetPath.getParent)
          val input = jarFile.getInputStream(entry)
          try Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)
          finally input.close()
        }
    } finally jarFile.close()
  }

  private def classpathPropertyEntries: List[String] =
    System
      .getProperty("java.class.path", "")
      .split(java.io.File.pathSeparator)
      .toList
      .filter(_.nonEmpty)

  private def contextClassLoaderEntries: List[String] =
    Thread.currentThread().getContextClassLoader match {
      case loader: URLClassLoader => loader.getURLs.toList.map(url => Paths.get(url.toURI).toString)
      case _ => Nil
    }

  private def knownClassLocations: List[Path] =
    List(
      classLocation(Derive.getClass),
      classLocation(classOf[cats.Functor[?]]),
      classLocation(classOf[Option[?]]),
      classLocation(Main.getClass)
    ).flatten

  private def classLocation(clazz: Class[?]): Option[Path] =
    for
      protectionDomain <- Option(clazz.getProtectionDomain)
      codeSource <- Option(protectionDomain.getCodeSource)
      location <- Option(codeSource.getLocation)
    yield Paths.get(location.toURI)

  private def gradleCacheJars: List[Path] = {
    val jars: List[Path] = List(
      cachedJar("org.typelevel", "cats-tagless-core_3"),
      cachedJar("org.typelevel", "cats-core_3"),
      cachedJar("org.typelevel", "cats-kernel_3"),
      cachedJar("org.scala-lang", "scala3-library_3"),
      cachedJar("org.scala-lang", "scala-library"),
      cachedJar("org.scala-lang", "scala3-interfaces"),
      cachedJar("org.scala-lang", "tasty-core_3"),
      cachedJar("org.scala-lang", "scala3-compiler_3")
    ).flatten

    jars
  }

  private def cachedJar(group: String, artifact: String): Option[Path] =
    cacheRoots
      .map(root => root.resolve(Paths.get("caches", "modules-2", "files-2.1", group, artifact)))
      .find(path => Files.isDirectory(path))
      .flatMap(findJar)

  private def cacheRoots: List[Path] =
    List(
      Option(System.getenv("GRADLE_USER_HOME")).map(Paths.get(_)),
      Some(Paths.get(System.getProperty("user.home"), ".gradle"))
    ).flatten ++ temporaryGradleCacheRoots

  private def temporaryGradleCacheRoots: List[Path] = {
    val root: Path = Paths.get("/tmp/metadata-forge-gradle")
    if !Files.isDirectory(root) then Nil
    else {
      val stream = Files.list(root)
      try stream.iterator().asScala.filter(path => Files.isDirectory(path.resolve("caches"))).toList
      finally stream.close()
    }
  }

  private def findJar(moduleDirectory: Path): Option[Path] = {
    val stream = Files.walk(moduleDirectory)
    try {
      stream
        .iterator()
        .asScala
        .filter(path => Files.isRegularFile(path))
        .filter(path => path.getFileName.toString.endsWith(".jar"))
        .filterNot(path => path.getFileName.toString.endsWith("-sources.jar"))
        .filterNot(path => path.getFileName.toString.endsWith("-javadoc.jar"))
        .toList
        .sortBy(path => Files.getLastModifiedTime(path).toMillis)
        .lastOption
    } finally stream.close()
  }
}

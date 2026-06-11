/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_tagless_core_3

import dotty.tools.dotc.Driver
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.annotation.experimental

@experimental
class MacroAspectTest:
  @Test
  def runtimeCompilerRunsAspectMacroForAlgWithGivenParameter(@TempDir tempDir: Path): Unit =
    try
      val sourceFile: Path = tempDir.resolve("RuntimeDerivedAspect.scala")
      val outputDirectory: Path = Files.createDirectories(tempDir.resolve("classes"))
      Files.writeString(sourceFile, compilerSource)

      val args: Array[String] = Array(
        "-experimental",
        "-classpath",
        runtimeClasspath,
        "-d",
        outputDirectory.toString,
        sourceFile.toString
      )
      val reporter: dotty.tools.dotc.reporting.Reporter = new Driver().process(args)

      assertFalse(reporter.hasErrors(), reporter.summary)
    catch
      case error: Error =>
        if !NativeImageSupport.isUnsupportedFeatureError(error) then throw error

  private def runtimeClasspath: String =
    val classLoaderChain: Iterator[ClassLoader] =
      Iterator.iterate(Thread.currentThread().getContextClassLoader)(_.getParent).takeWhile(_ != null)
    val loaderEntries: Seq[String] = classLoaderChain.toSeq.collect { case loader: URLClassLoader =>
      loader.getURLs.toSeq
    }.flatten.filter(_.getProtocol == "file").map(url => Paths.get(url.toURI).toString)
    val propertyEntries: Seq[String] = Option(System.getProperty("java.class.path"))
      .toSeq
      .flatMap(_.split(File.pathSeparator).toSeq)
      .filter(_.nonEmpty)

    (loaderEntries ++ propertyEntries).distinct.mkString(File.pathSeparator)

  private def compilerSource: String =
    """
      |package org_typelevel.cats_tagless_core_3.runtime_compiler
      |
      |import cats.Id
      |import cats.tagless.Derive
      |import cats.tagless.aop.Aspect
      |
      |import scala.annotation.experimental
      |
      |trait RuntimeAspectCapability[A]
      |
      |object RuntimeAspectCapability:
      |  given RuntimeAspectCapability[Int] with {}
      |  given RuntimeAspectCapability[String] with {}
      |
      |final case class RuntimeAspectContext(prefix: String)
      |
      |trait RuntimeAspectAlg[F[_]]:
      |  def describe(value: Int)(using context: RuntimeAspectContext): F[String]
      |
      |@experimental
      |object RuntimeDerivedAspect:
      |  given RuntimeAspectContext = RuntimeAspectContext("runtime")
      |
      |  val alg: RuntimeAspectAlg[Id] = new RuntimeAspectAlg[Id]:
      |    override def describe(value: Int)(using context: RuntimeAspectContext): Id[String] =
      |      s"${context.prefix}-$value"
      |
      |  val aspect: Aspect[RuntimeAspectAlg, RuntimeAspectCapability, RuntimeAspectCapability] =
      |    Derive.aspect[RuntimeAspectAlg, RuntimeAspectCapability, RuntimeAspectCapability]
      |  val woven: RuntimeAspectAlg[[A] =>> Aspect.Weave[Id, RuntimeAspectCapability, RuntimeAspectCapability, A]] =
      |    aspect.weave(alg)
      |  val advice: Aspect.Weave[Id, RuntimeAspectCapability, RuntimeAspectCapability, String] =
      |    woven.describe(41)
      |  val algebraName: String = advice.algebraName
      |  val domainArgumentName: String = advice.domain.head.head.name
      |  val domainArgumentValue: Any = advice.domain.head.head.target.value
      |  val codomainMethodName: String = advice.codomain.name
      |  val codomainValue: String = advice.codomain.target
      |""".stripMargin

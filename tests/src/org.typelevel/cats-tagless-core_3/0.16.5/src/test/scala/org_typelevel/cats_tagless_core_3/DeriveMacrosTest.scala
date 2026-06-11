/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_tagless_core_3

import cats.arrow.FunctionK
import cats.tagless.Derive
import dotty.tools.dotc.Driver
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.annotation.experimental

trait KeyedAlg[F[_]]:
  type Key
  def key: Key
  def read(key: Key): F[String]

@experimental
class DeriveMacrosTest:
  private val optionToList: FunctionK[Option, List] = new FunctionK[Option, List]:
    override def apply[A](fa: Option[A]): List[A] = fa.toList

  @Test
  def functorKDerivationPreservesAbstractTypeMembers(): Unit =
    val alg: KeyedAlg[Option] = new KeyedAlg[Option]:
      override type Key = Int
      override def key: Int = 7
      override def read(key: Int): Option[String] = Some(s"value-$key")

    val mapped: KeyedAlg[List] = Derive.functorK[KeyedAlg].mapK(alg)(optionToList)
    val copiedKey: mapped.Key = mapped.key

    assertEquals(7, copiedKey)
    assertEquals(List("value-7"), mapped.read(copiedKey))

  @Test
  def runtimeCompilerRunsDeriveMacroForAlgWithTypeMember(@TempDir tempDir: Path): Unit =
    assumeFalse(isNativeImageRuntime, "Scala 3 runtime compiler macro expansion is JVM-only")
    try
      val sourceFile: Path = tempDir.resolve("RuntimeDerivedFunctorK.scala")
      val outputDirectory: Path = Files.createDirectories(tempDir.resolve("classes"))
      Files.writeString(sourceFile, compilerSource)

      val classpath: String = runtimeClasspath
      val args: Array[String] = Array(
        "-experimental",
        "-classpath",
        classpath,
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
      |import cats.arrow.FunctionK
      |import cats.tagless.Derive
      |
      |import scala.annotation.experimental
      |
      |trait RuntimeKeyedAlg[F[_]]:
      |  type Key
      |  def key: Key
      |  def read(key: Key): F[String]
      |
      |@experimental
      |object RuntimeDerivedFunctorK:
      |  private val optionToList: FunctionK[Option, List] = new FunctionK[Option, List]:
      |    override def apply[A](fa: Option[A]): List[A] = fa.toList
      |
      |  val alg: RuntimeKeyedAlg[Option] = new RuntimeKeyedAlg[Option]:
      |    override type Key = Int
      |    override def key: Int = 9
      |    override def read(key: Int): Option[String] = Some(s"runtime-$key")
      |
      |  val mapped: RuntimeKeyedAlg[List] = Derive.functorK[RuntimeKeyedAlg].mapK(alg)(optionToList)
      |  val copiedKey: mapped.Key = mapped.key
      |  val copiedValue: List[String] = mapped.read(copiedKey)
      |""".stripMargin

  private def isNativeImageRuntime: Boolean =
    "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))

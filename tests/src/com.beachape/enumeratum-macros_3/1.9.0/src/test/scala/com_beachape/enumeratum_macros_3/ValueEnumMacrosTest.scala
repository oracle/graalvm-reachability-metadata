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
import enumeratum.ValueEnumMacros
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class ValueEnumMacrosTest {
  @Test
  def valueOfFromExprMappingReadsStaticModuleField(): Unit = {
    val macrosModuleClass: Class[?] = Class.forName("enumeratum.ValueEnumMacros$")
    val moduleField: java.lang.reflect.Field = macrosModuleClass.getField("MODULE$")
    val adapterMethod: java.lang.reflect.Method = macrosModuleClass.getDeclaredMethod(
      "enumeratum$ValueEnumMacros$ValueOfFromExpr$$_$unapply$$anonfun$adapted$1",
      classOf[java.lang.reflect.Field]
    )

    val extracted: ValueOf[?] =
      adapterMethod.invoke(null, moduleField).asInstanceOf[ValueOf[?]]

    assertSame(ValueEnumMacros, extracted.value)
  }

  @Test
  def runtimeCompilationFindsPrecompiledIntValueEntries(): Unit = {
    try {
      val workDir: Path = Files.createTempDirectory("enumeratum-value-enum-macros")
      val outputDir: Path = Files.createDirectories(workDir.resolve("classes"))
      val source: Path = workDir.resolve("RuntimeValueEnumMacrosProbe.scala")
      Files.writeString(
        source,
        """
          |package com_beachape.enumeratum_macros_3.runtime_compilation
          |
          |import com_beachape.enumeratum_macros_3.HighPriority
          |import com_beachape.enumeratum_macros_3.LowPriority
          |import com_beachape.enumeratum_macros_3.MediumPriority
          |import com_beachape.enumeratum_macros_3.Priority
          |import enumeratum.ValueEnumMacros
          |
          |object RuntimeValueEnumMacrosProbe {
          |  inline def discoveredValues: IndexedSeq[Priority] =
          |    ${ ValueEnumMacros.findIntValueEntriesImpl[Priority] }
          |
          |  val values: IndexedSeq[Priority] = discoveredValues
          |  val valueSum: Int = values.map(_.value).sum
          |  val hasExpectedOrder: Boolean =
          |    values == IndexedSeq(LowPriority, MediumPriority, HighPriority)
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

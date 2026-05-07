/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.sourcecode_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Sourcecode_2_13Test {
  @Test
  def capturesSourceLocationAndLexicalContext(): Unit = {
    val capturedFile: String = sourcecode.File()
    val capturedFileName: String = sourcecode.FileName()
    val capturedPackage: String = sourcecode.Pkg()
    val capturedName: String = sourcecode.Name()
    val capturedFullName: String = sourcecode.FullName()
    val capturedEnclosing: String = sourcecode.Enclosing()
    val firstLine: Int = sourcecode.Line()
    val secondLine: Int = sourcecode.Line()

    assertTrue(capturedFile.replace('\\', '/').endsWith("Sourcecode_2_13Test.scala"), capturedFile)
    assertEquals("Sourcecode_2_13Test.scala", capturedFileName)
    assertEquals("com_lihaoyi.sourcecode_2_13", capturedPackage)
    assertEquals("capturedName", capturedName)
    assertTrue(capturedFullName.contains("Sourcecode_2_13Test"), capturedFullName)
    assertTrue(capturedFullName.endsWith("capturedFullName"), capturedFullName)
    assertTrue(
      capturedEnclosing.contains("Sourcecode_2_13Test#capturesSourceLocationAndLexicalContext"),
      capturedEnclosing
    )
    assertTrue(capturedEnclosing.endsWith(" capturedEnclosing"), capturedEnclosing)
    assertEquals(firstLine + 1, secondLine)
  }

  @Test
  def materializesImplicitSourceValuesAndAcceptsManualOverrides(): Unit = {
    val generatedLine: Int = currentLine()
    val nextGeneratedLine: Int = currentLine()

    assertEquals("materializesImplicitSourceValuesAndAcceptsManualOverrides", currentName())
    assertEquals("manually supplied", currentName()(sourcecode.Name("manually supplied")))
    assertTrue(
      currentFullName().endsWith(".Sourcecode_2_13Test.materializesImplicitSourceValuesAndAcceptsManualOverrides")
    )
    assertEquals("fixed.full.name", currentFullName()(sourcecode.FullName("fixed.full.name")))
    assertTrue(
      currentEnclosing().contains("Sourcecode_2_13Test#materializesImplicitSourceValuesAndAcceptsManualOverrides")
    )
    assertEquals("fixed enclosing", currentEnclosing()(sourcecode.Enclosing("fixed enclosing")))
    assertEquals("Sourcecode_2_13Test.scala", currentFileName())
    assertEquals(generatedLine + 1, nextGeneratedLine)
    assertEquals(12345, currentLine()(sourcecode.Line(12345)))
  }

  @Test
  def exposesMachineOrientedSyntheticContextValues(): Unit = {
    class ConstructorNamed(implicit val name: sourcecode.Name.Machine)
    object LocalSingleton extends ConstructorNamed

    object LocalMachineContexts {
      val machineName: String = sourcecode.Name.Machine()
      val machineFullName: String = sourcecode.FullName.Machine()
      val machineEnclosing: String = sourcecode.Enclosing.Machine()
    }

    assertEquals("<init>", LocalSingleton.name.value)
    assertEquals("machineName", LocalMachineContexts.machineName)
    assertTrue(
      LocalMachineContexts.machineFullName.contains("Sourcecode_2_13Test"),
      LocalMachineContexts.machineFullName
    )
    assertTrue(LocalMachineContexts.machineFullName.endsWith("LocalMachineContexts.machineFullName"), LocalMachineContexts.machineFullName)
    assertTrue(LocalMachineContexts.machineEnclosing.contains("exposesMachineOrientedSyntheticContextValues"), LocalMachineContexts.machineEnclosing)
    assertTrue(LocalMachineContexts.machineEnclosing.endsWith("LocalMachineContexts.machineEnclosing"), LocalMachineContexts.machineEnclosing)
  }

  @Test
  def capturesTextSourceForApplyAndImplicitConversion(): Unit = {
    val explicitText: sourcecode.Text[Seq[Int]] = sourcecode.Text(Seq(1).map(_ + 1))
    val base: Int = 7
    val implicitText: sourcecode.Text[Int] = captureText(base * 6)
    val chainedText: sourcecode.Text[String] = sourcecode.Text(List("a", "b").mkString("-").toUpperCase)

    assertEquals(Seq(2), explicitText.value)
    assertEquals("Seq(1).map(_ + 1)", explicitText.source)
    assertEquals(42, implicitText.value)
    assertEquals("base * 6", implicitText.source)
    assertEquals("A-B", chainedText.value)
    assertEquals("""List("a", "b").mkString("-").toUpperCase""", chainedText.source)
  }

  @Test
  def capturesArgumentsForMethodsAndImplicitLists(): Unit = {
    implicit val token: String = "secret-token"

    val methodArgs: Seq[Seq[(String, Any)]] = argsFromMethod("alpha", 3)(enabled = true)
    val implicitArgs: Seq[Seq[(String, Any)]] = argsFromMethodWithImplicit("beta")
    val noArgs: Seq[Seq[(String, Any)]] = argsFromMethodWithoutParameters()

    assertEquals(Seq(Seq("first" -> "alpha", "count" -> 3), Seq("enabled" -> true)), methodArgs)
    assertEquals(Seq(Seq("first" -> "beta"), Seq("token" -> "secret-token")), implicitArgs)
    assertEquals(Seq(Seq.empty), noArgs)
  }

  @Test
  def wrapsRawValuesForSourceValueParameters(): Unit = {
    assertEquals("wrapped-name", nameValue("wrapped-name"))
    assertEquals("wrapped.full.Name", fullNameValue("wrapped.full.Name"))
    assertEquals("/tmp/Sourcecode_2_13Test.scala", fileValue("/tmp/Sourcecode_2_13Test.scala"))
    assertEquals("Sourcecode_2_13Test.scala", fileNameValue("Sourcecode_2_13Test.scala"))
    assertEquals("com_lihaoyi.sourcecode_2_13", packageValue("com_lihaoyi.sourcecode_2_13"))
    assertEquals("Sourcecode_2_13Test#wrappedRawValuesForSourceValueParameters", enclosingValue("Sourcecode_2_13Test#wrappedRawValuesForSourceValueParameters"))
    assertEquals("machine-name", machineNameValue("machine-name"))
    assertEquals("machine.full.Name", machineFullNameValue("machine.full.Name"))
    assertEquals("Machine#enclosing", machineEnclosingValue("Machine#enclosing"))
    assertEquals(321, lineValue(321))
  }

  @Test
  def supportsSourceValueExtractorsAndCopies(): Unit = {
    val line: sourcecode.Line = sourcecode.Line(10)
    val copiedLine: sourcecode.Line = line.copy(value = 11)
    val file: sourcecode.File = sourcecode.File("Input.scala")
    val machineName: sourcecode.Name.Machine = sourcecode.Name.Machine("syntheticName")
    val text: sourcecode.Text[Int] = sourcecode.Text(42, "answer")
    val args: sourcecode.Args = sourcecode.Args(Seq(Seq(text)))

    val sourcecode.Line(extractedLine) = line
    val sourcecode.File(extractedFile) = file
    val sourcecode.Name.Machine(extractedMachineName) = machineName
    val sourcecode.Text(extractedValue, extractedSource) = text
    val sourcecode.Args(extractedArgs) = args

    assertEquals(10, extractedLine)
    assertEquals("Input.scala", extractedFile)
    assertEquals("syntheticName", extractedMachineName)
    assertEquals(42, extractedValue)
    assertEquals("answer", extractedSource)
    assertEquals(Seq(Seq(text)), extractedArgs)
    assertEquals(sourcecode.Line(11), copiedLine)
    assertEquals(sourcecode.File("Copied.scala"), file.copy(value = "Copied.scala"))
    assertEquals(sourcecode.Name.Machine("copiedSyntheticName"), machineName.copy(value = "copiedSyntheticName"))
    assertEquals(sourcecode.Text(43, "answer"), text.copy(value = 43))
    assertEquals(sourcecode.Args(Seq.empty), args.copy(value = Seq.empty))
  }

  private def nameValue(name: sourcecode.Name): String = name.value

  private def fullNameValue(fullName: sourcecode.FullName): String = fullName.value

  private def fileValue(file: sourcecode.File): String = file.value

  private def fileNameValue(fileName: sourcecode.FileName): String = fileName.value

  private def packageValue(pkg: sourcecode.Pkg): String = pkg.value

  private def enclosingValue(enclosing: sourcecode.Enclosing): String = enclosing.value

  private def machineNameValue(name: sourcecode.Name.Machine): String = name.value

  private def machineFullNameValue(fullName: sourcecode.FullName.Machine): String = fullName.value

  private def machineEnclosingValue(enclosing: sourcecode.Enclosing.Machine): String = enclosing.value

  private def lineValue(line: sourcecode.Line): Int = line.value

  private def currentName()(implicit name: sourcecode.Name): String = name.value

  private def currentFullName()(implicit fullName: sourcecode.FullName): String = fullName.value

  private def currentEnclosing()(implicit enclosing: sourcecode.Enclosing): String = enclosing.value

  private def currentFileName()(implicit fileName: sourcecode.FileName): String = fileName.value

  private def currentLine()(implicit line: sourcecode.Line): Int = line.value

  private def captureText[T](text: sourcecode.Text[T]): sourcecode.Text[T] = text

  private def captureCurrentArgs(implicit arguments: sourcecode.Args): Seq[Seq[(String, Any)]] = {
    arguments.value.map(_.map(text => text.source -> text.value))
  }

  private def argsFromMethod(first: String, count: Int)(enabled: Boolean): Seq[Seq[(String, Any)]] = {
    captureCurrentArgs
  }

  private def argsFromMethodWithImplicit(first: String)(implicit token: String): Seq[Seq[(String, Any)]] = {
    captureCurrentArgs
  }

  private def argsFromMethodWithoutParameters(): Seq[Seq[(String, Any)]] = {
    captureCurrentArgs
  }

}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.sourcecode_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNotEquals, assertThrows, assertTrue}
import org.junit.jupiter.api.Test

import scala.language.implicitConversions

class Sourcecode_3Test {
  @Test
  def capturesFileLineAndPackageAtCompileTime(): Unit = {
    val file: sourcecode.File = implicitly[sourcecode.File]
    val fileName: sourcecode.FileName = implicitly[sourcecode.FileName]
    val pkg: sourcecode.Pkg = implicitly[sourcecode.Pkg]
    val firstLine: Int = captureLine
    val secondLine: Int = captureLine

    assertPointsAtThisTest(file.value)
    assertEquals("Sourcecode_3Test.scala", fileName.value)
    assertEquals("com_lihaoyi.sourcecode_3", pkg.value)
    assertEquals(firstLine + 1, secondLine)
    assertTrue(firstLine > 0, s"Expected a positive line number, got $firstLine")

    assertPointsAtThisTest(sourcecode.File())
    assertEquals("Sourcecode_3Test.scala", sourcecode.FileName())
    assertEquals("com_lihaoyi.sourcecode_3", sourcecode.Pkg())
    assertTrue(sourcecode.Line() > secondLine)
  }

  @Test
  def capturesDefinitionNamesAndEnclosingPaths(): Unit = {
    val name: sourcecode.Name = implicitly[sourcecode.Name]
    val fullName: sourcecode.FullName = implicitly[sourcecode.FullName]
    val enclosing: sourcecode.Enclosing = implicitly[sourcecode.Enclosing]

    assertEquals("name", name.value)
    assertEquals(
      "com_lihaoyi.sourcecode_3.Sourcecode_3Test.fullName",
      fullName.value
    )
    assertTrue(
      enclosing.value.contains("com_lihaoyi.sourcecode_3.Sourcecode_3Test#capturesDefinitionNamesAndEnclosingPaths"),
      s"Expected enclosing path to include this test method, got ${enclosing.value}"
    )
    assertTrue(enclosing.value.endsWith("enclosing"), s"Expected enclosing path to end at the local val, got ${enclosing.value}")

    val contextualName: String = captureName
    val contextualFullName: String = captureFullName

    assertEquals("contextualName", contextualName)
    assertTrue(
      contextualFullName.endsWith("Sourcecode_3Test.contextualFullName"),
      s"Expected contextual full name to include the local val, got $contextualFullName"
    )
  }

  @Test
  def exposesMachineNamesWithoutFilteringSyntheticOwners(): Unit = {
    val name: sourcecode.Name.Machine = implicitly[sourcecode.Name.Machine]
    val fullName: sourcecode.FullName.Machine = implicitly[sourcecode.FullName.Machine]
    val enclosing: sourcecode.Enclosing.Machine = implicitly[sourcecode.Enclosing.Machine]

    assertTrue(name.value.nonEmpty, "Expected a non-empty machine name")
    assertTrue(
      fullName.value.contains("Sourcecode_3Test"),
      s"Expected machine full name to include this class, got ${fullName.value}"
    )
    assertTrue(
      enclosing.value.contains("exposesMachineNamesWithoutFilteringSyntheticOwners"),
      s"Expected machine enclosing path to include this test method, got ${enclosing.value}"
    )
  }

  @Test
  def capturesSourceTextForExpressionsAndByNameValues(): Unit = {
    val base: Int = 7
    val expression: sourcecode.Text[Int] = captureText(base + 5)
    val block: sourcecode.Text[String] = captureText {
      val word: String = "source"
      word.reverse
    }
    val direct: sourcecode.Text[List[Int]] = sourcecode.Text(List(1, 2, 3).map(_ * 2))

    assertEquals(12, expression.value)
    assertEquals("base + 5", normalizeWhitespace(expression.source))
    assertEquals("ecruos", block.value)
    assertEquals("word.reverse", normalizeWhitespace(block.source))
    assertEquals(List(2, 4, 6), direct.value)
    assertEquals("List(1, 2, 3).map(_ * 2)", normalizeWhitespace(direct.source))
  }

  @Test
  def capturesArgumentsFromNearestEnclosingMethod(): Unit = {
    val args: sourcecode.Args = collectArgs("alpha", 21 + 21)(flag = true)
    val argumentLists: List[List[sourcecode.Text[_]]] = args.value.map(_.toList).toList

    assertEquals(2, argumentLists.size)
    assertEquals(List("first", "second"), argumentLists.head.map(_.source))
    assertEquals(List(None, None), argumentLists.head.map(_.value))
    assertEquals(List("flag"), argumentLists(1).map(_.source))
    assertEquals(List(None), argumentLists(1).map(_.value))
  }

  @Test
  def capturesArgumentsFromNearestEnclosingConstructor(): Unit = {
    val probe: ConstructorArgProbe = new ConstructorArgProbe("alpha", 42)(enabled = true)
    val argumentLists: List[List[sourcecode.Text[_]]] = probe.capturedArgs.value.map(_.toList).toList

    assertEquals(2, argumentLists.size)
    assertEquals(List("label", "count"), argumentLists.head.map(_.source))
    assertEquals(List(None, None), argumentLists.head.map(_.value))
    assertEquals(List("enabled"), argumentLists(1).map(_.source))
    assertEquals(List(None), argumentLists(1).map(_.value))
  }

  @Test
  def allowsExplicitContextValuesToOverrideGeneratedValues(): Unit = {
    val explicitFile: sourcecode.File = sourcecode.File("Manual.scala")
    val explicitLine: sourcecode.Line = sourcecode.Line(123)
    val explicitName: sourcecode.Name = sourcecode.Name("ManualName")
    val explicitFullName: sourcecode.FullName = sourcecode.FullName("manual.FullName")
    val explicitEnclosing: sourcecode.Enclosing = sourcecode.Enclosing("manual.Enclosing")
    val explicitPkg: sourcecode.Pkg = sourcecode.Pkg("manual.pkg")

    assertEquals("Manual.scala", captureFile(using explicitFile).value)
    assertEquals(123, captureLineValue(using explicitLine))
    assertEquals("ManualName", captureNameValue(using explicitName))
    assertEquals("manual.FullName", captureFullNameValue(using explicitFullName))
    assertEquals("manual.Enclosing", captureEnclosingValue(using explicitEnclosing))
    assertEquals("manual.pkg", capturePkgValue(using explicitPkg))

    assertEquals("Manual.scala", sourcecode.File()(using explicitFile))
    assertEquals(123, sourcecode.Line()(using explicitLine))
    assertEquals("ManualName", sourcecode.Name()(using explicitName))
    assertEquals("manual.FullName", sourcecode.FullName()(using explicitFullName))
    assertEquals("manual.Enclosing", sourcecode.Enclosing()(using explicitEnclosing))
    assertEquals("manual.pkg", sourcecode.Pkg()(using explicitPkg))
  }

  @Test
  def wrapsRawValuesAsSourceContextValues(): Unit = {
    val convertedFileName: sourcecode.FileName = "Converted.scala"
    val convertedMachineName: sourcecode.Name.Machine = "machineName"
    val argument: sourcecode.Text[Option[Int]] = sourcecode.Text(Some(42), "answer")
    val convertedArgs: sourcecode.Args = Seq(Seq(argument))

    assertEquals("Converted.scala", convertedFileName.value)
    assertEquals("machineName", convertedMachineName.value)
    assertEquals(Seq(Seq(argument)), convertedArgs.value)

    assertEquals("Context.scala", captureFileNameValue(using "Context.scala"))
    assertEquals("rawMachineFullName", captureMachineFullNameValue(using "rawMachineFullName"))
    assertEquals("rawMachineEnclosing", captureMachineEnclosingValue(using "rawMachineEnclosing"))
    assertEquals(Seq(Seq(argument)), captureArgs(using Seq(Seq(argument))).value)
  }

  @Test
  def sourceValuesProvideCaseClassSemantics(): Unit = {
    val line: sourcecode.Line = sourcecode.Line(10)
    val copiedLine: sourcecode.Line = line.copy(value = 11)
    val file: sourcecode.File = sourcecode.File("Input.scala")
    val text: sourcecode.Text[Int] = sourcecode.Text(42, "answer")
    val args: sourcecode.Args = sourcecode.Args(Seq(Seq(text)))

    val sourcecode.Line(extractedLine) = line
    val sourcecode.File(extractedFile) = file
    val sourcecode.Text(extractedValue, extractedSource) = text
    val sourcecode.Args(extractedArgs) = args

    assertEquals(10, extractedLine)
    assertEquals("Input.scala", extractedFile)
    assertEquals(42, extractedValue)
    assertEquals("answer", extractedSource)
    assertEquals(Seq(Seq(text)), extractedArgs)
    assertEquals(sourcecode.Line(10), line)
    assertNotEquals(line, copiedLine)
    assertEquals("Line(10)", line.toString)
    assertEquals(1, line.productArity)
    assertEquals("Line", line.productPrefix)
    assertEquals(10, line.productElement(0))
    assertEquals("value", line.productElementName(0))
    assertEquals(List("value"), line.productElementNames.toList)
    assertEquals(List(10), line.productIterator.toList)
    assertTrue(line.canEqual(sourcecode.Line(0)))
    assertFalse(line.canEqual(10))
    assertThrows(classOf[IndexOutOfBoundsException], () => {
      line.productElement(1)
      ()
    })

    assertEquals(2, text.productArity)
    assertEquals("Text", text.productPrefix)
    assertEquals(42, text.productElement(0))
    assertEquals("answer", text.productElement(1))
    assertEquals(sourcecode.Text(43, "answer"), text.copy(value = 43))
    assertEquals(sourcecode.Args(Seq(Seq(text))), args.copy())
  }

  private def captureLine(using line: sourcecode.Line): Int = line.value

  private def captureLineValue(using line: sourcecode.Line): Int = line.value

  private def captureFile(using file: sourcecode.File): sourcecode.File = file

  private def captureFileNameValue(using fileName: sourcecode.FileName): String = fileName.value

  private def captureName(using name: sourcecode.Name): String = name.value

  private def captureNameValue(using name: sourcecode.Name): String = name.value

  private def captureFullName(using fullName: sourcecode.FullName): String = fullName.value

  private def captureFullNameValue(using fullName: sourcecode.FullName): String = fullName.value

  private def captureMachineFullNameValue(using fullName: sourcecode.FullName.Machine): String = fullName.value

  private def captureEnclosingValue(using enclosing: sourcecode.Enclosing): String = enclosing.value

  private def captureMachineEnclosingValue(using enclosing: sourcecode.Enclosing.Machine): String = enclosing.value

  private def capturePkgValue(using pkg: sourcecode.Pkg): String = pkg.value

  private def captureText[T](value: sourcecode.Text[T]): sourcecode.Text[T] = value

  private def collectArgs(first: String, second: Int)(flag: Boolean): sourcecode.Args = {
    captureArgs
  }

  private def captureArgs(using args: sourcecode.Args): sourcecode.Args = args

  private class ConstructorArgProbe(label: String, count: Int)(enabled: Boolean) {
    val capturedArgs: sourcecode.Args = captureArgs
  }

  private def assertPointsAtThisTest(file: String): Unit = {
    val normalizedFile: String = file.replace('\\', '/')

    assertTrue(
      normalizedFile.endsWith("/Sourcecode_3Test.scala") || normalizedFile == "Sourcecode_3Test.scala",
      s"Expected file to refer to Sourcecode_3Test.scala, got $file"
    )
  }

  private def normalizeWhitespace(text: String): String = text.replaceAll("\\s+", " ").trim
}

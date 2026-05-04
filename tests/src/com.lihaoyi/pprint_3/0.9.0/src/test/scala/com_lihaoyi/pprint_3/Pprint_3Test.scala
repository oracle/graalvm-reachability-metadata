/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.pprint_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import scala.collection.immutable.ListMap

import pprint.PPrinter
import pprint.TPrintColors
import pprint.Tree

class Pprint_3Test {
  private final case class Address(city: String, zip: Int)
  private final case class Person(name: String, age: Int, address: Address)
  private final case class Secret(token: String)

  @Test
  def rendersPrimitiveAndStringLiterals(): Unit = {
    val printer: PPrinter = PPrinter.BlackWhite

    assertEquals("null", printer(null).plainText)
    assertEquals("true", printer(true).plainText)
    assertEquals("'\\n'", printer('\n').plainText)
    assertEquals("123", printer(123).plainText)
    assertEquals("123L", printer(123L).plainText)
    assertEquals("1.5F", printer(1.5F).plainText)
    assertEquals("1.5", printer(1.5D).plainText)
    assertEquals("\"hello\\tworld\"", printer("hello\tworld").plainText)
    assertEquals("\"snowman ☃\"", printer("snowman ☃", escapeUnicode = false).plainText)
    assertEquals("\"snowman \\u2603\"", printer("snowman ☃", escapeUnicode = true).plainText)
    assertEquals("\"\"\"first\nsecond\"\"\"", printer("first\nsecond").plainText)
  }

  @Test
  def rendersCollectionsArraysMapsOptionsAndIterators(): Unit = {
    val printer: PPrinter = PPrinter.BlackWhite
    val orderedMap: ListMap[String, Int] = ListMap("one" -> 1, "two" -> 2)

    assertEquals("List(1, 2, 3)", printer(List(1, 2, 3)).plainText)
    assertEquals("Vector(\"a\", \"b\")", printer(Vector("a", "b")).plainText)
    val mapText: String = printer(orderedMap).plainText

    assertEquals("Array(\"x\", \"y\")", printer(Array("x", "y")).plainText)
    assertTrue(mapText.startsWith("ListMap(") || mapText.startsWith("Map("))
    assertTrue(mapText.contains("\"one\" -> 1"))
    assertTrue(mapText.contains("\"two\" -> 2"))
    assertEquals("Some(value = 42)", printer(Some(42)).plainText)
    assertEquals("Some(42)", printer(Some(42), showFieldNames = false).plainText)
    assertEquals("None", printer(None).plainText)
    assertEquals("empty iterator", printer(Iterator.empty).plainText)
    assertEquals("non-empty iterator", printer(Iterator.single("value")).plainText)
  }

  @Test
  def rendersProductsWithAndWithoutFieldNames(): Unit = {
    val person: Person = Person("Ada", 37, Address("London", 12345))
    val printer: PPrinter = PPrinter.BlackWhite

    assertEquals(
      "Person(name = \"Ada\", age = 37, address = Address(city = \"London\", zip = 12345))",
      printer(person, width = 120).plainText
    )
    assertEquals(
      "Person(\"Ada\", 37, Address(\"London\", 12345))",
      printer(person, width = 120, showFieldNames = false).plainText
    )
    assertEquals("(\"left\", 99)", printer(("left", 99)).plainText)
  }

  @Test
  def rendersSymbolicProductsAsInfixExpressions(): Unit = {
    final case class ::(head: Any, tail: Any)
    val printer: PPrinter = PPrinter.BlackWhite

    assertEquals("1 :: 2", printer(::(1, 2)).plainText)
    assertEquals("0 :: 1 :: 2", printer(::(0, ::(1, 2))).plainText)
  }

  @Test
  def wrapsIndentedOutputAndTruncatesTallOutput(): Unit = {
    val printer: PPrinter = PPrinter.BlackWhite

    assertEquals(
      "Vector(\n    \"alpha\",\n    \"beta\",\n    \"gamma\"\n)",
      printer(Vector("alpha", "beta", "gamma"), width = 20, indent = 4).plainText
    )

    val truncated: String = printer((1 to 20).toVector, width = 10, height = 3).plainText
    assertTrue(truncated.startsWith("Vector(\n"))
    assertTrue(truncated.endsWith("..."))
    assertTrue(truncated.linesIterator.length <= 3)
  }

  @Test
  def exposesTokenStreamAndColorConfiguration(): Unit = {
    val blackWhiteTokens: Vector[fansi.Str] = PPrinter.BlackWhite.tokenize(List(1, 2), width = 80).toVector
    val blackWhiteText: String = blackWhiteTokens.map(_.plainText).mkString
    val colored: fansi.Str = PPrinter.Color(List(1, 2), width = 80)
    val blackWhite: fansi.Str = PPrinter.BlackWhite(List(1, 2), width = 80)

    assertEquals("List(1, 2)", blackWhiteText)
    assertTrue(blackWhiteTokens.map(_.plainText).contains("List"))
    assertTrue(colored.getColors.exists(_ != 0L))
    assertFalse(blackWhite.getColors.exists(_ != 0L))
  }

  @Test
  def supportsCustomAdditionalHandlers(): Unit = {
    val redactingHandler: PartialFunction[Any, Tree] = {
      case Secret(_) => Tree.Literal("<redacted>")
    }
    val printer: PPrinter = PPrinter.BlackWhite.copy(additionalHandlers = redactingHandler)

    assertEquals("<redacted>", printer(Secret("open sesame")).plainText)
    assertEquals(
      "Person(name = \"Grace\", age = 42, address = Address(city = \"Arlington\", zip = 22207))",
      printer(Person("Grace", 42, Address("Arlington", 22207)), width = 120).plainText
    )
  }

  @Test
  def printsCompileTimeTypeRepresentations(): Unit = {
    val (listType, tupleType, functionType): (String, String, String) = {
      given TPrintColors = TPrintColors.BlackWhite

      (
        pprint.tprint[List[Option[Int]]].plainText,
        pprint.tprint[(String, Int)].plainText,
        pprint.tprint[String => Int].plainText
      )
    }
    val coloredType: fansi.Str = {
      given TPrintColors = TPrintColors.Colors.Colored

      pprint.tprint[Either[String, Int]]
    }

    assertEquals("List[Option[Int]]", listType)
    assertEquals("(String, Int)", tupleType)
    assertEquals("String => Int", functionType)
    assertEquals("Either[String, Int]", coloredType.plainText)
    assertTrue(coloredType.getColors.exists(_ != 0L))
  }

  @Test
  def printsAndLogsThroughPublicConvenienceMethods(): Unit = {
    val printedOutput: String = captureStandardOut {
      PPrinter.BlackWhite.pprintln(List("x", "y"), width = 80)
    }
    assertEquals(s"List(\"x\", \"y\")${System.lineSeparator()}", printedOutput)

    val loggedOutput: String = captureStandardOut {
      val value: List[Int] = List(1, 2, 3)
      val returned: List[Int] = PPrinter.BlackWhite.log(value, tag = "numbers", width = 80)
      assertEquals(value, returned)
    }
    val plainLoggedOutput: String = stripAnsi(loggedOutput)

    assertTrue(plainLoggedOutput.contains("Pprint_3Test.scala:"))
    assertTrue(plainLoggedOutput.contains("value:"))
    assertTrue(plainLoggedOutput.contains("numbers"))
    assertTrue(plainLoggedOutput.contains("List(1, 2, 3)"))
  }

  @Test
  def logsThroughErrFacadeToStandardError(): Unit = {
    val stdout: String = captureStandardOut {
      val stderr: String = captureStandardErr {
        val value: Vector[String] = Vector("warning", "details")
        val returned: Vector[String] = PPrinter.BlackWhite.err.log(value, tag = "stderr", width = 80)
        assertEquals(value, returned)
      }
      val plainStderr: String = stripAnsi(stderr)

      assertTrue(plainStderr.contains("stderr"))
      assertTrue(plainStderr.contains("Vector(\"warning\", \"details\")"))
    }

    assertEquals("", stdout)
  }

  private def captureStandardOut(body: => Unit): String = {
    val buffer: ByteArrayOutputStream = new ByteArrayOutputStream()
    val stream: PrintStream = new PrintStream(buffer, true, StandardCharsets.UTF_8)
    try {
      Console.withOut(stream) {
        body
      }
      stream.flush()
      new String(buffer.toByteArray, StandardCharsets.UTF_8)
    } finally {
      stream.close()
    }
  }

  private def captureStandardErr(body: => Unit): String = {
    val buffer: ByteArrayOutputStream = new ByteArrayOutputStream()
    val stream: PrintStream = new PrintStream(buffer, true, StandardCharsets.UTF_8)
    try {
      Console.withErr(stream) {
        body
      }
      stream.flush()
      new String(buffer.toByteArray, StandardCharsets.UTF_8)
    } finally {
      stream.close()
    }
  }

  private def stripAnsi(value: String): String = {
    value.replaceAll("\u001B\\[[;\\d]*m", "")
  }
}

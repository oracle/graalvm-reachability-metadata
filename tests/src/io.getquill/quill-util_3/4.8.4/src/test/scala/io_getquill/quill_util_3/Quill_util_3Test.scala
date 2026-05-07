/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_getquill.quill_util_3

import io.getquill.util.ScalafmtFormat
import io.getquill.util.ThrowableOps._
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Quill_util_3Test {
  @Test
  def scalafmtFormatFormatsScalaDefinitionsWithDefaultStyle(): Unit = {
    val unformatted: String =
      """object   Greeter{case class Person(name:String,age:Int)
        |def  greet(person:Person):String={
        |val message="hello, "+ person.name
        |message
        |}}
        |""".stripMargin

    val formatted: String = ScalafmtFormat(unformatted)

    assertThat(formatted).contains(
      """object Greeter {
        |  case class Person(name: String, age: Int)
        |""".stripMargin
    )
    assertThat(formatted).contains(
      """  def greet(person: Person): String = {
        |    val message = "hello, " + person.name
        |    message
        |  }
        |""".stripMargin
    )
    assertThat(formatted.trim).endsWith("}")
  }

  @Test
  def scalafmtFormatProducesStableOutputAfterFormattingOnce(): Unit = {
    val unformattedCode: String =
      """object MathSyntax{val numbers:List[Int]=List(1,2,3)
        |def doubled:List[Int]=numbers.map(value=>value*2)}
        |""".stripMargin

    val firstFormatting: String = ScalafmtFormat(unformattedCode)
    val secondFormatting: String = ScalafmtFormat(firstFormatting)

    assertThat(secondFormatting).isEqualTo(firstFormatting)
    assertThat(firstFormatting).contains("val numbers: List[Int] = List(1, 2, 3)")
    assertThat(firstFormatting).contains("numbers.map(value => value * 2)")
  }

  @Test
  def scalafmtFormatPreservesCommentsWhileFormattingCode(): Unit = {
    val unformattedCode: String =
      """object Commented{/* Builds a public greeting. */
        |def greet(name:String):String={
        |// Keep the user-provided name visible.
        |val message="hello, "+name // trailing greeting comment
        |message
        |}}
        |""".stripMargin

    val formatted: String = ScalafmtFormat(unformattedCode)

    assertThat(formatted).contains("/* Builds a public greeting. */")
    assertThat(formatted).contains("// Keep the user-provided name visible.")
    assertThat(formatted).contains("// trailing greeting comment")
    assertThat(formatted).contains("def greet(name: String): String = {")
    assertThat(formatted).contains("val message = \"hello, \" + name")
  }

  @Test
  def scalafmtFormatFormatsForComprehensionsWithGuards(): Unit = {
    val unformattedCode: String =
      """object Queries{def pairs(numbers:List[Int],names:List[String]):List[(Int,String)]=for{number<-numbers
        |if number%2==0
        |name<-names if name.nonEmpty}yield(number,name)}
        |""".stripMargin

    val formatted: String = ScalafmtFormat(unformattedCode)

    assertThat(formatted).contains(
      "def pairs(numbers: List[Int], names: List[String]): List[(Int, String)] ="
    )
    assertThat(formatted).contains("for {")
    assertThat(formatted).contains("number <- numbers")
    assertThat(formatted).contains("if number % 2 == 0")
    assertThat(formatted).contains("name <- names")
    assertThat(formatted).contains("if name.nonEmpty")
    assertThat(formatted).contains("} yield (number, name)")
  }

  @Test
  def scalafmtFormatReturnsOriginalCodeForParseFailuresWithoutTraceByDefault(): Unit = {
    val invalidCode: String = "object Broken { def value: Int = "
    val consoleOutput: ByteArrayOutputStream = new ByteArrayOutputStream()

    val result: String = Console.withOut(new PrintStream(consoleOutput, true, StandardCharsets.UTF_8)) {
      ScalafmtFormat(invalidCode)
    }

    assertThat(result).isEqualTo(invalidCode)
    assertThat(consoleOutput.toString(StandardCharsets.UTF_8)).isEmpty()
  }

  @Test
  def scalafmtFormatReturnsOriginalCodeAndPrintsTraceWhenRequested(): Unit = {
    val invalidCode: String = "object Broken { def value: Int = "
    val consoleOutput: ByteArrayOutputStream = new ByteArrayOutputStream()

    val result: String = Console.withOut(new PrintStream(consoleOutput, true, StandardCharsets.UTF_8)) {
      ScalafmtFormat(invalidCode, showErrorTrace = true)
    }

    val output: String = consoleOutput.toString(StandardCharsets.UTF_8)
    assertThat(result).isEqualTo(invalidCode)
    assertThat(output).contains("===== Failed to format the code ====")
    assertThat(output).contains(invalidCode)
    assertThat(output).contains("org.scalafmt")
  }

  @Test
  def throwableOpsRendersStackTraceWithTypeMessageAndCause(): Unit = {
    val cause: IllegalArgumentException = new IllegalArgumentException("bad input")
    val failure: IllegalStateException = new IllegalStateException("formatting failed", cause)

    val stackTrace: String = failure.stackTraceToString

    assertThat(stackTrace).contains("java.lang.IllegalStateException: formatting failed")
    assertThat(stackTrace).contains("Caused by: java.lang.IllegalArgumentException: bad input")
    assertThat(stackTrace).contains("throwableOpsRendersStackTraceWithTypeMessageAndCause")
  }

  @Test
  def throwableOpsCanRenderSuppressedExceptionsAndRemainRepeatable(): Unit = {
    val failure: RuntimeException = new RuntimeException("primary")
    failure.addSuppressed(new UnsupportedOperationException("suppressed detail"))

    val firstRendering: String = failure.stackTraceToString
    val secondRendering: String = failure.stackTraceToString

    assertThat(firstRendering).isEqualTo(secondRendering)
    assertThat(firstRendering).contains("java.lang.RuntimeException: primary")
    assertThat(firstRendering).contains("Suppressed: java.lang.UnsupportedOperationException: suppressed detail")
  }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_parser_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import zio.Chunk
import zio.parser.Parser
import zio.parser.ParserImplementation
import zio.parser.Printer
import zio.parser.Regex
import zio.parser.StringParserError
import zio.parser.Syntax
import zio.parser._

class Zio_parser_2_13Test {
  @Test
  def parserCombinatorsParseSignedIntegersWithBothImplementations(): Unit = {
    val signedInt: Parser[String, Char, Int] =
      ((Parser.char('-').optional ~ Parser.digit.repeat.string) <~ Parser.end).map { case (sign, digits) =>
        val magnitude: Int = digits.toInt
        if (sign.isDefined) -magnitude else magnitude
      }

    Seq(ParserImplementation.StackSafe, ParserImplementation.Recursive).foreach { implementation =>
      assertEquals(Right(-12345), signedInt.parseString("-12345", implementation))
      assertEquals(Right(987), signedInt.parseString("987", implementation))
    }

    assertLeftContains(signedInt.parseString("12x"), "Parser did not consume all input")
    assertLeftContains(signedInt.parseString("-"), "Unexpected end of input")
  }

  @Test
  def recursiveParsersAndExplicitBacktrackingConsumeNestedInput(): Unit = {
    lazy val nestedParentheses: Parser[String, Char, Int] =
      ((Parser.char('(') ~> nestedParentheses <~ Parser.char(')')).map(_ + 1) | Parser.succeed(0))

    val completeNestedParentheses: Parser[String, Char, Int] = nestedParentheses <~ Parser.end
    assertEquals(Right(3), completeNestedParentheses.parseString("((()))"))
    assertEquals(Right(0), completeNestedParentheses.parseString(""))

    val keywordOrIdentifier: Parser[String, Char, String] =
      (((Parser.string("let", "keyword").as("keyword") <~ Parser.char(' ')).backtrack) |
        Parser.letter.repeat.string.map(value => s"identifier:$value")) <~ Parser.end

    assertEquals(Right("keyword"), keywordOrIdentifier.parseString("let "))
    assertEquals(Right("identifier:letter"), keywordOrIdentifier.parseString("letter"))
  }

  @Test
  def parserNegativeLookaheadRejectsMatchingInputWithoutConsumingOnSuccess(): Unit = {
    val nonDigitCharacter: Parser[String, Char, Char] =
      (Parser.digit.not("digit") ~> Parser.anyChar) <~ Parser.end

    assertEquals(Right('a'), nonDigitCharacter.parseString("a"))
    assertEquals(Right('#'), nonDigitCharacter.parseString("#"))
    assertLeftContains(nonDigitCharacter.parseString("7"), "digit")
  }

  @Test
  def parsersHandleCharacterChunksAndGenericChunks(): Unit = {
    val word: Parser[String, Char, String] = Parser.letter.repeat.string <~ Parser.end
    assertEquals(Right("abcXYZ"), word.parseChars(Chunk('a', 'b', 'c', 'X', 'Y', 'Z')))

    val sumPair: Parser[Nothing, Int, Int] =
      ((Parser.any[Int] ~ Parser.any[Int]).map { case (left, right) => left + right }) <~ Parser.end

    assertEquals(Right(15), sumPair.parseChunk(Chunk(7, 8)))
  }

  @Test
  def parserFailuresPreserveNamesAndPrettyContext(): Unit = {
    val bracketedLetter: Parser[String, Char, Char] =
      (Parser.char('[') ~> Parser.letter.named("letter") <~ Parser.char(']')) <~ Parser.end

    bracketedLetter.parseString("[7]") match {
      case Left(error) =>
        val pretty: String = error.pretty
        assertTrue(pretty.contains("Failure at position 1"), pretty)
        assertTrue(pretty.contains("not a letter"), pretty)
        assertTrue(pretty.contains("letter"), pretty)
      case Right(value) => fail(s"Expected parsing to fail, but it succeeded with $value")
    }
  }

  @Test
  def syntaxRoundTripsSeparatedIdentifiersAndEmptyLists(): Unit = {
    val csvIdentifiers: Syntax[String, Char, Char, List[String]] =
      (identifier.repeatWithSep0(Syntax.char(',')) <~ Syntax.end).toList[String]

    val values: List[String] = List("alpha1", "Beta2", "c3")
    assertEquals(Right(values), csvIdentifiers.parseString("alpha1,Beta2,c3"))
    assertEquals(Right("alpha1,Beta2,c3"), csvIdentifiers.printString(values))

    assertEquals(Right(List.empty[String]), csvIdentifiers.parseString(""))
    assertEquals(Right(""), csvIdentifiers.printString(List.empty[String]))
    assertLeftContains((identifier <~ Syntax.end).parseString("1alpha"), "not a letter")
  }

  @Test
  def syntaxTransformEitherValidatesParsingAndPrinting(): Unit = {
    val evenDigit: Syntax[String, Char, Char, Int] =
      Syntax.digit.transformEither[String, Int](
        (char: Char) => {
          val digit: Int = char.asDigit
          if (digit % 2 == 0) Right(digit) else Left("odd digit")
        },
        (digit: Int) =>
          if (digit >= 0 && digit <= 9 && digit % 2 == 0) Right(('0' + digit).toChar)
          else Left("not an even digit")
      )

    val completeEvenDigit: Syntax[String, Char, Char, Int] = evenDigit <~ Syntax.end
    assertEquals(Right(4), completeEvenDigit.parseString("4"))
    assertEquals(Right("8"), evenDigit.printString(8))
    assertLeftContains(completeEvenDigit.parseString("5"), "odd digit")
    assertEquals(Left("not an even digit"), evenDigit.printString(3))
  }

  @Test
  def syntaxAlternativesSelectMatchingParserAndPrinterBranch(): Unit = {
    val booleanSyntax: Syntax[String, Char, Char, Boolean] =
      Syntax.string("yes", true) | Syntax.string("no", false)
    val completeBooleanSyntax: Syntax[String, Char, Char, Boolean] = booleanSyntax <~ Syntax.end

    assertEquals(Right(true), completeBooleanSyntax.parseString("yes"))
    assertEquals(Right(false), completeBooleanSyntax.parseString("no"))
    assertEquals(Right("yes"), booleanSyntax.printString(true))
    assertEquals(Right("no"), booleanSyntax.printString(false))
    assertLeftContains(completeBooleanSyntax.parseString("maybe"), "All branches failed")
  }

  @Test
  def printersComposeRepeatValidateAndChooseByValue(): Unit = {
    val digitPrinter: Printer[String, Char, Int] =
      Printer.digit
        .contramap[Int](digit => ('0' + digit).toChar)
        .filter((digit: Int) => digit >= 0 && digit <= 9, "not digit")
    val commaSeparatedDigits: Printer[String, Char, Chunk[Int]] = digitPrinter.repeatWithSep0(Printer.char(','))

    assertEquals(Right("1,2,3"), commaSeparatedDigits.printString(Chunk(1, 2, 3)))
    assertEquals(Right(""), commaSeparatedDigits.printString(Chunk.empty[Int]))
    assertEquals(Left("not digit"), commaSeparatedDigits.printString(Chunk(10)))

    val valuePrinter: Printer[String, Char, Either[String, Int]] =
      Printer.byValue[String, Char, Either[String, Int]] {
        case Left(text)  => Printer.printString(text)
        case Right(int)  => Printer.printString(int.toString)
      }
    val prefixedValuePrinter: Printer[String, Char, Either[String, Int]] =
      Printer.string("value=", ()) ~> valuePrinter

    assertEquals(Right("value=42"), prefixedValuePrinter.printString(Right(42)))
    assertEquals(Right("value=ready"), prefixedValuePrinter.printString(Left("ready")))
  }

  @Test
  def printersSupportNonCharacterOutputs(): Unit = {
    val exactlySeven: Printer[String, Int, Int] = Printer.exactly(7)
    assertEquals(Right(Chunk(7)), exactlySeven.print(7))
    assertEquals(Left("not '7'"), exactlySeven.print(8))

    val exceptZero: Printer[String, Int, Int] = Printer.except(0)
    assertEquals(Right(Chunk(5)), exceptZero.print(5))
    assertEquals(Left("cannot be '0'"), exceptZero.print(0))
  }

  @Test
  def regexCompilationSupportsSequencesRepetitionAlternationAndLiteralExtraction(): Unit = {
    val token: Regex = Regex.string("ab") ~ Regex.digits
    val compiledToken: Regex.Compiled = token.compile

    assertEquals(5, compiledToken.test(0, "ab123!"))
    assertEquals(Regex.NotMatched, compiledToken.test(0, "ac123"))
    assertEquals(Regex.NeedMoreInput, compiledToken.test(0, "ab"))
    assertTrue(compiledToken.matches("ab9"))

    val chunkInput: Chunk[Char] = Chunk.fromIterable("xxab42".toList)
    assertEquals(6, compiledToken.test(2, chunkInput))

    assertEquals(Some(Chunk('O', 'K')), Regex.string("OK").toLiteral)
    assertEquals(None, Regex.digits.toLiteral)
  }

  @Test
  def regexCompilationSupportsIntersectionAndNegatedCharacterSets(): Unit = {
    val upperHexLetter: Regex = Regex.anyLetter & Regex.charIn('A', 'B', 'C', 'D', 'E', 'F')
    val hexDigit: Regex = Regex.anyDigit | upperHexLetter

    assertEquals(1, hexDigit.compile.test(0, "F"))
    assertEquals(1, hexDigit.compile.test(0, "7"))
    assertEquals(Regex.NotMatched, hexDigit.compile.test(0, "g"))

    val notX: Regex.Compiled = Regex.charNotIn('x').compile
    assertEquals(1, notX.test(0, "a"))
    assertEquals(Regex.NotMatched, notX.test(0, "x"))
    assertFalse(Regex.char('q').compile.matches("z"))
  }

  private def identifier: Syntax[String, Char, Char, String] =
    (Syntax.letter ~ Syntax.alphaNumeric.repeat0).transform[String](
      { case (head, tail) => head.toString + tail.mkString },
      value => (value.head, Chunk.fromIterable(value.tail))
    )

  private def assertLeftContains[Err, Value](result: Either[StringParserError[Err], Value], expected: String): Unit =
    result match {
      case Left(error) =>
        val pretty: String = error.pretty
        assertTrue(pretty.contains(expected), s"Expected '$pretty' to contain '$expected'")
      case Right(value) => fail(s"Expected parsing to fail with '$expected', but it succeeded with $value")
    }
}

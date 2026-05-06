/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_parse_3

import cats.data.NonEmptyList
import cats.parse.Caret
import cats.parse.LocationMap
import cats.parse.Numbers
import cats.parse.Parser
import cats.parse.Parser0
import cats.parse.SemVer
import cats.parse.strings.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Cats_parse_3Test {
  @Test
  def primitiveParsersConsumeCharactersAndReportLeftovers(): Unit = {
    val identifier: Parser[String] =
      (Parser.charWhere(_.isLetter) ~ Parser.charsWhile0(ch => ch.isLetterOrDigit || ch == '_')).map {
        case (head, tail) => s"$head$tail"
      }

    assertEquals(Right(("=42", "answer_1")), identifier.parse("answer_1=42"))
    assertEquals(Right("answer_1"), identifier.parseAll("answer_1"))

    val error = identifier.parseAll("1answer") match {
      case Left(value) => value
      case Right(value) => throw new AssertionError(s"Expected parse failure, got $value")
    }
    assertEquals(0, error.failedAtOffset)
    assertFalse(error.expected.toList.isEmpty)
  }

  @Test
  def alternativesBacktrackingAndContextParseKeywords(): Unit = {
    val ifKeyword: Parser[String] = (Parser.string("if") <* Parser.not(Parser.charWhere(_.isLetter))).as("if").backtrack
    val identifier: Parser[String] = Parser.charsWhile(_.isLetter).withContext("identifier")
    val token: Parser[String] = ifKeyword | identifier

    assertEquals(Right("if"), token.parseAll("if"))
    assertEquals(Right("iff"), token.parseAll("iff"))
    assertEquals(Right("while"), token.parseAll("while"))

    val error = token.parseAll("123") match {
      case Left(value) => value
      case Right(value) => throw new AssertionError(s"Expected parse failure, got $value")
    }
    assertEquals(0, error.failedAtOffset)
    assertTrue(error.expected.toList.exists(_.context.contains("identifier")))
  }

  @Test
  def repetitionsSeparatorsAndDelimitersBuildStructuredValues(): Unit = {
    val whitespace: Parser0[Unit] = Parser.charsWhile0(_.isWhitespace).void
    val comma: Parser0[Unit] = (whitespace *> Parser.char(',') <* whitespace).void
    val key: Parser[String] = Parser.charsWhile(_.isLetter)
    val value: Parser[Int] = Numbers.signedIntString.map(_.toInt)
    val assignment: Parser[(String, Int)] =
      ((key <* whitespace <* Parser.char('=') <* whitespace) ~ value).between(Parser.char('['), Parser.char(']'))
    val assignments: Parser0[List[(String, Int)]] = assignment.repSep0(comma)

    assertEquals(Right(List(("x", 1), ("y", -20), ("z", 300))), assignments.parseAll("[x = 1], [y=-20],[z=300]"))
    assertEquals(Right(List.empty[(String, Int)]), assignments.parseAll(""))

    val nonEmptyAssignments: Parser[NonEmptyList[(String, Int)]] = assignment.repSep(comma)
    val parsed: NonEmptyList[(String, Int)] = nonEmptyAssignments.parseAll("[alpha=10]") match {
      case Right(value) => value
      case Left(error) => throw new AssertionError(s"Expected successful parse, got $error")
    }
    assertEquals(List(("alpha", 10)), parsed.toList)
  }

  @Test
  def optionalPeekNotAndWithStringSupportTokenLookahead(): Unit = {
    val sign: Parser0[Option[Unit]] = Parser.char('-').?
    val digitsWithSource: Parser[(String, String)] = Numbers.digits.withString.map {
      case (value, source) => (value, source)
    }
    val signedDigits: Parser0[(Boolean, (String, String))] =
      (sign.map(_.isDefined) ~ (Parser.not(Parser.char('0') *> Parser.end) *> digitsWithSource))

    assertEquals(Right((true, ("123", "123"))), signedDigits.parseAll("-123"))
    assertEquals(Right((false, ("123", "123"))), signedDigits.parseAll("123"))

    val zeroError = signedDigits.parseAll("0") match {
      case Left(value) => value
      case Right(value) => throw new AssertionError(s"Expected parse failure, got $value")
    }
    assertEquals(0, zeroError.failedAtOffset)

    val lookahead: Parser0[(Unit, String)] = Parser.peek(Parser.string("let")) ~ Parser.charsWhile(_.isLetter)
    assertEquals(Right(((), "letter")), lookahead.parseAll("letter"))
  }

  @Test
  def numericParsersRecognizeIntegerAndJsonNumberForms(): Unit = {
    assertEquals(Right("0"), Numbers.nonNegativeIntString.parseAll("0"))
    assertEquals(Right("12345"), Numbers.nonNegativeIntString.parseAll("12345"))
    assertEquals(Right("-987"), Numbers.signedIntString.parseAll("-987"))
    assertEquals(Right(BigInt("123456789012345678901234567890")), Numbers.bigInt.parseAll("123456789012345678901234567890"))

    val jsonNumbers: List[String] = List("0", "-12", "3.1415", "6.02e23", "-1.5E-2")
    jsonNumbers.foreach { input =>
      assertEquals(Right(input), Numbers.jsonNumber.parseAll(input))
    }

    assertTrue(Numbers.nonNegativeIntString.parseAll("01").isLeft)
    assertTrue(Numbers.jsonNumber.parseAll("1.").isLeft)
  }

  @Test
  def recursiveParserEvaluatesNestedArithmeticExpressions(): Unit = {
    val whitespace: Parser0[Unit] = Parser.charsWhile0(_.isWhitespace).void
    def token[A](parser: Parser[A]): Parser[A] = parser.surroundedBy(whitespace)

    lazy val expression: Parser[Int] = Parser.recursive[Int] { recurse =>
      val number: Parser[Int] = token(Numbers.signedIntString.map(_.toInt))
      val parens: Parser[Int] = recurse.between(token(Parser.char('(')), token(Parser.char(')'))).backtrack
      val atom: Parser[Int] = parens | number
      val operator: Parser[Char] = token(Parser.charIn(List('+', '-')))

      (atom ~ (operator ~ atom).rep0).map { case (first, rest) =>
        rest.foldLeft(first) {
          case (accumulator, ('+', next)) => accumulator + next
          case (accumulator, ('-', next)) => accumulator - next
          case (accumulator, (operator, _)) => throw new AssertionError(s"Unexpected operator: $operator")
        }
      }
    }

    assertEquals(Right(10), expression.parseAll("1 + 2 + (10 - 3)"))
    assertEquals(Right(-4), expression.parseAll("(1 - 2) - (3 + 0)"))
    assertTrue(expression.parseAll("1 + (2 -").isLeft)
  }

  @Test
  def mapFilterCollectAndFromMapsChooseTypedValues(): Unit = {
    val evenDigit: Parser[Int] = Numbers.digit.map(_.asDigit).mapFilter { digit =>
      if digit % 2 == 0 then Some(digit) else None
    }
    val command: Parser[String] = Parser.fromStringMap(Map("add" -> "ADD", "remove" -> "REMOVE"))
    val grade: Parser[Int] = Parser.fromCharMap(Map('A' -> 100, 'B' -> 85, 'C' -> 70))

    assertEquals(Right(8), evenDigit.parseAll("8"))
    assertTrue(evenDigit.parseAll("7").isLeft)
    assertEquals(Right("REMOVE"), command.parseAll("remove"))
    assertEquals(Right(("-now", "ADD")), command.parse("add-now"))
    assertEquals(Right(85), grade.parseAll("B"))

    val lowercase: Parser[Char] = Parser.anyChar.collect { case char if char.isLower => char }
    assertEquals(Right('z'), lowercase.parseAll("z"))
    assertTrue(lowercase.parseAll("Z").isLeft)
  }

  @Test
  def locationMapAndCaretTranslateOffsetsAndLines(): Unit = {
    val input: String = "alpha\nbeta\ngamma"
    val locations: LocationMap = LocationMap(input)

    assertEquals(3, locations.lineCount)
    assertEquals(Some("beta"), locations.getLine(1))
    assertEquals(Some((1, 2)), locations.toLineCol(8))
    assertEquals(Some(8), locations.toOffset(1, 2))
    assertEquals(Some(Caret(1, 2, 8)), locations.toCaret(8))
    assertFalse(locations.isValidOffset(input.length + 1))

    val caretParser: Parser0[(Caret, String)] = Parser.caret ~ Parser.length(5)
    assertEquals(Right((Caret.Start, "alpha")), caretParser.parseAll("alpha"))
  }

  @Test
  def stringAndCaseInsensitiveParsersHandleLiteralMatching(): Unit = {
    val keyword: Parser[Unit] = Parser.ignoreCase("select")
    val yesNo: Parser[String] = Parser.stringIn(List("yes", "no", "maybe"))
    val vowels: Parser[Char] = Parser.ignoreCaseCharIn(List('a', 'e', 'i', 'o', 'u'))

    assertEquals(Right(()), keyword.parseAll("SeLeCt"))
    assertEquals(Right("maybe"), yesNo.parseAll("maybe"))
    assertEquals(Right(("way", "yes")), yesNo.parse("yesway"))
    assertEquals(Right('O'), vowels.parseAll("O"))
    assertTrue(Parser.ignoreCase("select").parseAll("insert").isLeft)
  }

  @Test
  def jsonStringCodecsDecodeEscapesAndEncodeControlCharacters(): Unit = {
    val original: String = "line\nquote\"slash\\tab\t"
    val encoded: String = Json.delimited.encode(original)

    assertEquals("\"line\\nquote\\\"slash\\\\tab\\t\"", encoded)
    assertEquals(Right(original), Json.delimited.parser.parseAll(encoded))
    assertEquals(Right(("tail", "snowman ☃")), Json.delimited.parser.parse("\"snowman \\u2603\"tail"))
    assertTrue(Json.delimited.parser.parseAll("\"unterminated").isLeft)

    val undelimited: String = Json.undelimited.encode(original)
    assertEquals("line\\nquote\\\"slash\\\\tab\\t", undelimited)
    assertEquals(Right(original), Json.undelimited.parser.parseAll(undelimited))
    assertEquals(Right("plain text"), Json.undelimited.parser.parseAll("plain text"))
    assertEquals(Right(""), Json.undelimited.parser.parseAll(""))
  }

  @Test
  def semanticVersionParsersExtractCorePreReleaseAndBuildMetadata(): Unit = {
    val release: SemVer.SemVer = SemVer.semver.parseAll("2.3.5-alpha.1+build.7") match {
      case Right(value) => value
      case Left(error) => throw new AssertionError(s"Expected semantic version, got $error")
    }

    assertEquals("2", release.core.major)
    assertEquals("3", release.core.minor)
    assertEquals("5", release.core.patch)
    assertEquals(Some("alpha.1"), release.preRelease)
    assertEquals(Some("build.7"), release.buildMetadata)

    assertEquals(Right("2.3.5"), SemVer.coreString.parseAll("2.3.5"))
    assertEquals(Right("alpha.1"), SemVer.preRelease.parseAll("alpha.1"))
    assertEquals(Right("build.7"), SemVer.build.parseAll("build.7"))
    assertTrue(SemVer.semver.parseAll("02.3.5").isLeft)
    assertTrue(SemVer.preRelease.parseAll("alpha_1").isLeft)
  }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_parsing_3

import java.nio.charset.StandardCharsets
import scala.util.Failure

import akka.parboiled2.CharPredicate
import akka.parboiled2.CharUtils
import akka.parboiled2.DynamicRuleDispatch
import akka.parboiled2.DynamicRuleHandler
import akka.parboiled2.ErrorFormatter
import akka.parboiled2.ParseError
import akka.parboiled2.Parser
import akka.parboiled2.ParserInput
import akka.parboiled2.Position
import akka.parboiled2.Rule0
import akka.parboiled2.Rule1
import akka.parboiled2.support.hlist.::
import akka.parboiled2.support.hlist.HNil
import akka.parboiled2.util.Base64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Akka_parsing_3Test {
  @Test
  def parsesTypedArithmeticAssignments(): Unit = {
    val parsedAssignment: Assignment = parseAssignment("answer = 1 + 2 * (3 + 4) - 5").get

    assertThat(parsedAssignment).isEqualTo(Assignment("answer", 10))
  }

  @Test
  def acceptsDifferentParserInputImplementations(): Unit = {
    val charInput: ParserInput = ParserInput("total = 6 * 7".toCharArray)
    val bytes: Array[Byte] = "limited = 99 + ignored".getBytes(StandardCharsets.ISO_8859_1)
    val byteInput: ParserInput = ParserInput(bytes, "limited = 99".length)

    assertThat(new AssignmentParser(charInput).InputLine.run().get).isEqualTo(Assignment("total", 42))
    assertThat(new AssignmentParser(byteInput).InputLine.run().get).isEqualTo(Assignment("limited", 99))
    assertThat(byteInput.sliceString(0, 7)).isEqualTo("limited")
    assertThat(ParserInput("first\nsecond\nthird").getLine(2)).isEqualTo("second")
    assertThat(ParserInput("abcdef").sliceCharArray(2, 5)).containsExactly('c', 'd', 'e')
  }

  @Test
  def reportsFormattedParseErrorsWithPositionsAndExpectedInput(): Unit = {
    val parser: AssignmentParser = new AssignmentParser(ParserInput("answer = 1 + ?"))
    val result: Failure[Assignment] = parser.InputLine.run().asInstanceOf[Failure[Assignment]]
    val error: ParseError = result.exception.asInstanceOf[ParseError]
    val formatted: String = parser.formatError(error, new ErrorFormatter(showTraces = true, expandTabs = 2))

    assertThat(error.position.line).isEqualTo(1)
    assertThat(error.position.column).isEqualTo(14)
    assertThat(formatted).contains("Invalid input '?'")
    assertThat(formatted).contains("line 1, column 14")
    assertThat(formatted).contains("^")
    assertThat(formatted).contains("Factor")
  }

  @Test
  def matchesCaseInsensitiveValuesAndRejectsUnknownCommands(): Unit = {
    assertThat(new CommandParser(ParserInput("ADD")).Command.run().get).isEqualTo("create")
    assertThat(new CommandParser(ParserInput("remove")).Command.run().get).isEqualTo("delete")

    val rejected: Failure[String] = new CommandParser(ParserInput("rename")).Command.run().asInstanceOf[Failure[String]]
    val error: ParseError = rejected.exception.asInstanceOf[ParseError]

    assertThat(error.position.index).isEqualTo(2)
    assertThat(new ErrorFormatter(showLine = false).formatExpectedAsString(error)).isEqualTo("'m'")
  }

  @Test
  def dispatchesNamedRulesDynamically(): Unit = {
    val (dispatch, ruleNames) = DynamicRuleDispatch[DynamicTokenParser, String :: HNil]("Word", "Number")

    assertThat(ruleNames.toList).isEqualTo(List("Word", "Number"))
    assertThat(dispatch(new DynamicTokenParser(ParserInput("Scala")), "Word")).isEqualTo(Right("Scala"))
    assertThat(dispatch(new DynamicTokenParser(ParserInput("12345")), "Number")).isEqualTo(Right("12345"))
    assertThat(dispatch(new DynamicTokenParser(ParserInput("12345")), "Word").swap.toOption.get).contains("Invalid input '1'")
    assertThat(dispatch(new DynamicTokenParser(ParserInput("Scala")), "Missing")).isEqualTo(Left("missing:Missing"))
  }

  @Test
  def combinesAndQueriesCharacterPredicates(): Unit = {
    val identifierPart: CharPredicate = (CharPredicate.AlphaNum ++ '_') -- CharPredicate.Digit
    val latinLetters: CharPredicate = CharPredicate('é', 'ß')
    val allowed: CharPredicate = identifierPart ++ latinLetters

    assertThat(identifierPart.isMaskBased).isTrue
    assertThat(latinLetters.isMaskBased).isFalse
    assertThat(allowed.matchesAll("Name_éß")).isTrue
    assertThat(allowed.matchesAny("123_456")).isTrue
    assertThat(allowed.indexOfFirstMatch("123_456")).isEqualTo(3)
    assertThat(allowed.firstMismatch("Abc-Def")).isEqualTo(Some('-'))
    assertThat((CharPredicate.Printable -- CharPredicate.Digit).intersect(CharPredicate.AlphaNum).matchesAll("abcXYZ")).isTrue
    assertThat(CharPredicate.Empty.negated.matchesAny("x")).isTrue
  }

  @Test
  def parsesSeparatedValueListsAndOptionalQueryFlags(): Unit = {
    val multiValueQuery: IdQuery = new IdQueryParser(ParserInput("features?ids=3,5,8,13&verbose")).Query.run().get
    val singleValueQuery: IdQuery = new IdQueryParser(ParserInput("feature_42?ids=21")).Query.run().get

    assertThat(multiValueQuery).isEqualTo(IdQuery("features", Seq(3, 5, 8, 13), verbose = true))
    assertThat(singleValueQuery).isEqualTo(IdQuery("feature_42", Seq(21), verbose = false))
  }

  @Test
  def usesLookaheadAndNegativePredicatesForDelimiters(): Unit = {
    assertThat(new DelimitedTokenParser(ParserInput("alpha,beta")).Token.run().get).isEqualTo("alpha")
    assertThat(new DelimitedTokenParser(ParserInput("omega")).Token.run().get).isEqualTo("omega")

    val rejected: Failure[String] = new DelimitedTokenParser(ParserInput(",empty")).Token.run().asInstanceOf[Failure[String]]
    val error: ParseError = rejected.exception.asInstanceOf[ParseError]

    assertThat(error.position.index).isEqualTo(0)
  }

  @Test
  def encodesAndDecodesBase64Payloads(): Unit = {
    val payload: Array[Byte] = "Akka parsing?".getBytes(StandardCharsets.UTF_8)
    val rfc2045: Base64 = Base64.rfc2045()
    val custom: Base64 = Base64.custom()

    val rfcEncoded: String = rfc2045.encodeToString(payload, false)
    val customEncoded: String = custom.encodeToString(payload, false)

    assertThat(rfcEncoded).isEqualTo("QWtrYSBwYXJzaW5nPw==")
    assertThat(customEncoded).isEqualTo("QWtrYSBwYXJzaW5nPw__")
    assertThat(new String(rfc2045.decode("\n" + rfcEncoded + "\r\n"), StandardCharsets.UTF_8)).isEqualTo("Akka parsing?")
    assertThat(new String(custom.decodeFast(customEncoded), StandardCharsets.UTF_8)).isEqualTo("Akka parsing?")
    assertThat(rfc2045.encodeToChar(Array.emptyByteArray, false)).isEmpty
  }

  @Test
  def parsesFixedWidthRepetitionsAndOptionalRuleValues(): Unit = {
    val colorWithAlpha: HexColor = new HexColorParser(ParserInput("#1a2B3c/80")).Color.run().get
    val opaqueColor: HexColor = new HexColorParser(ParserInput("#AABBCC")).Color.run().get

    assertThat(colorWithAlpha).isEqualTo(HexColor(26, 43, 60, Some(128)))
    assertThat(opaqueColor).isEqualTo(HexColor(170, 187, 204, None))

    val rejected: Failure[HexColor] = new HexColorParser(ParserInput("#ff00xz")).Color.run().asInstanceOf[Failure[HexColor]]
    val error: ParseError = rejected.exception.asInstanceOf[ParseError]

    assertThat(error.position.index).isEqualTo(5)
  }

  @Test
  def handlesCharacterUtilitiesAndInputPositions(): Unit = {
    val input: ParserInput = ParserInput("first\nsecond\nthird")
    val position: Position = Position(8, input)

    assertThat(position).isEqualTo(Position(8, 2, 3))
    assertThat(CharUtils.hexValue('f')).isEqualTo(15)
    assertThat(CharUtils.lowerHexString(255L)).isEqualTo("ff")
    assertThat(CharUtils.upperHexString(48879L)).isEqualTo("BEEF")
    assertThat(CharUtils.signedDecimalString(Long.MinValue)).isEqualTo(Long.MinValue.toString)
    assertThat(CharUtils.escape("line\n\t")).isEqualTo("line\\n\\t")
    assertThat(CharUtils.upperHexDigit(15)).isEqualTo('F')
    assertThat(CharUtils.lowerHexDigit(15)).isEqualTo('f')
  }

  private def parseAssignment(input: String): scala.util.Try[Assignment] =
    new AssignmentParser(ParserInput(input)).InputLine.run()
}

final case class Assignment(name: String, value: Int)

final case class HexColor(red: Int, green: Int, blue: Int, alpha: Option[Int])

final case class IdQuery(resource: String, ids: Seq[Int], verbose: Boolean)

final class AssignmentParser(val input: ParserInput) extends Parser {
  def InputLine: Rule1[Assignment] = rule { Spacing ~ AssignmentRule ~ Spacing ~ EOI }

  def AssignmentRule: Rule1[Assignment] = rule {
    Identifier ~ Spacing ~ '=' ~ Spacing ~ Expression ~> ((name: String, value: Int) => Assignment(name, value))
  }

  def Expression: Rule1[Int] = rule {
    Term ~ zeroOrMore(
      Spacing ~ '+' ~ Spacing ~ Term ~> ((left: Int, right: Int) => left + right)
        | Spacing ~ '-' ~ Spacing ~ Term ~> ((left: Int, right: Int) => left - right)
    )
  }

  def Term: Rule1[Int] = rule {
    Factor ~ zeroOrMore(
      Spacing ~ '*' ~ Spacing ~ Factor ~> ((left: Int, right: Int) => left * right)
        | Spacing ~ '/' ~ Spacing ~ Factor ~> ((left: Int, right: Int) => left / right)
    )
  }

  def Factor: Rule1[Int] = rule { Number | '(' ~ Spacing ~ Expression ~ Spacing ~ ')' }

  def Number: Rule1[Int] = rule { capture(optional('-') ~ oneOrMore(CharPredicate.Digit)) ~> ((digits: String) => digits.toInt) }

  def Identifier: Rule1[String] = rule {
    capture(CharPredicate.Alpha ~ zeroOrMore(CharPredicate.AlphaNum ++ '_'))
  }

  def Spacing: Rule0 = rule { zeroOrMore(anyOf(" \n\r\t")) }
}

final class CommandParser(val input: ParserInput) extends Parser {
  private val commandValues: Map[String, String] = Map("add" -> "create", "remove" -> "delete")

  def Command: Rule1[String] = rule { valueMap(commandValues, ignoreCase = true) ~ EOI }
}

final class IdQueryParser(val input: ParserInput) extends Parser {
  def Query: Rule1[IdQuery] = rule {
    Resource ~ "?ids=" ~ Ids ~ OptionalVerbose ~ EOI ~> ((resource: String, ids: Seq[Int], verbose: Boolean) =>
      IdQuery(resource, ids, verbose))
  }

  def Resource: Rule1[String] = rule { capture(oneOrMore(CharPredicate.AlphaNum ++ '_')) }

  def Ids: Rule1[Seq[Int]] = rule { oneOrMore(Id).separatedBy(',') }

  def Id: Rule1[Int] = rule { capture(oneOrMore(CharPredicate.Digit)) ~> ((digits: String) => digits.toInt) }

  def OptionalVerbose: Rule1[Boolean] = rule { "&verbose" ~ push(true) | push(false) }
}

final class DelimitedTokenParser(val input: ParserInput) extends Parser {
  def Token: Rule1[String] = rule { !ch(',') ~ capture(oneOrMore(noneOf(","))) ~ (&(ch(',')) | EOI) }
}

final class HexColorParser(val input: ParserInput) extends Parser {
  def Color: Rule1[HexColor] = rule {
    '#' ~ Component ~ Component ~ Component ~ optional('/' ~ Component) ~ EOI ~> (
      (red: Int, green: Int, blue: Int, alpha: Option[Int]) => HexColor(red, green, blue, alpha))
  }

  def Component: Rule1[Int] = rule {
    capture(2.times(HexDigit)) ~> ((digits: String) => Integer.parseInt(digits, 16))
  }

  def HexDigit: Rule0 = rule { predicate(CharPredicate.HexDigit) }
}

final class DynamicTokenParser(val input: ParserInput) extends Parser with DynamicRuleHandler[DynamicTokenParser, String :: HNil] {
  def Word: Rule1[String] = rule { capture(oneOrMore(CharPredicate.Alpha)) ~ EOI }

  def Number: Rule1[String] = rule { capture(oneOrMore(CharPredicate.Digit)) ~ EOI }

  type Result = Either[String, String]

  def parser: DynamicTokenParser = this

  def ruleNotFound(ruleName: String): Result = Left(s"missing:$ruleName")

  def success(result: String :: HNil): Result = Right(result.head)

  def parseError(error: ParseError): Result = Left(formatError(error))

  def failure(error: Throwable): Result = Left(error.getMessage)
}

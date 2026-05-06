/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_parboiled.parboiled_2_13

import java.nio.charset.StandardCharsets
import scala.util.Failure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.parboiled2.Base64Parsing
import org.parboiled2.CharPredicate
import org.parboiled2.DynamicRuleDispatch
import org.parboiled2.DynamicRuleHandler
import org.parboiled2.ErrorFormatter
import org.parboiled2.ParseError
import org.parboiled2.Parser
import org.parboiled2.ParserInput
import org.parboiled2.Rule0
import org.parboiled2.Rule1
import org.parboiled2.RuleRunner
import org.parboiled2.StringBuilding
import org.parboiled2.support.hlist.::
import org.parboiled2.support.hlist.HNil
import org.parboiled2.util.Base64

class Parboiled_2_13Test {
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
  def decodesBase64StringsAndBlocks(): Unit = {
    val payload: Array[Byte] = "parboiled parses bytes".getBytes(StandardCharsets.UTF_8)
    val rfc2045Encoded: String = Base64.rfc2045().encodeToString(payload, false)
    val customBlock: String = Base64.custom().encodeToString(payload ++ payload ++ payload ++ payload, false)

    val decodedString: Array[Byte] = new Base64RuleParser(ParserInput(rfc2045Encoded)).rfc2045String.run().get
    val decodedBlock: Array[Byte] = new Base64RuleParser(ParserInput(customBlock)).base64CustomBlock.run().get

    assertThat(new String(decodedString, StandardCharsets.UTF_8)).isEqualTo("parboiled parses bytes")
    assertThat(new String(decodedBlock, StandardCharsets.UTF_8)).isEqualTo("parboiled parses bytes" * 4)
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
  def buildsQuotedTextWithStringBuilderActions(): Unit = {
    val parsed: String = new QuotedTextParser(ParserInput("\"Hello\\n\\\"Scala\\\"\\\\3\"")).QuotedText.run().get

    assertThat(parsed).isEqualTo("Hello\n\"Scala\"\\3")
  }

  @Test
  def parsesSeparatedValueLists(): Unit = {
    val multiValueQuery: IdQuery = new IdQueryParser(ParserInput("features?ids=3,5,8,13")).Query.run().get
    val singleValueQuery: IdQuery = new IdQueryParser(ParserInput("feature_42?ids=21")).Query.run().get

    assertThat(multiValueQuery).isEqualTo(IdQuery("features", Seq(3, 5, 8, 13)))
    assertThat(singleValueQuery).isEqualTo(IdQuery("feature_42", Seq(21)))
  }

  @Test
  def parsesFixedWidthHexColorsWithCountedRepetition(): Unit = {
    val color: RgbColor = new HexColorParser(ParserInput("#1a2B3c")).HexColor.run().get
    val tooShort: Failure[RgbColor] = new HexColorParser(ParserInput("#12345")).HexColor.run().asInstanceOf[Failure[RgbColor]]

    assertThat(color).isEqualTo(RgbColor(0x1a, 0x2b, 0x3c))
    assertThat(tooShort.exception).isInstanceOf(classOf[ParseError])
  }

  @Test
  def usesLookaheadPredicatesWithoutConsumingInput(): Unit = {
    val directive: String = new LookaheadDirectiveParser(ParserInput("token:release-2026")).Directive.run().get
    val nonReservedIdentifier: String = new LookaheadDirectiveParser(ParserInput("token:debugger")).Directive.run().get
    val reservedIdentifier: Failure[String] =
      new LookaheadDirectiveParser(ParserInput("token:debug")).Directive.run().asInstanceOf[Failure[String]]

    assertThat(directive).isEqualTo("release-2026")
    assertThat(nonReservedIdentifier).isEqualTo("debugger")
    assertThat(reservedIdentifier.exception).isInstanceOf(classOf[ParseError])
  }

  private def parseAssignment(input: String): scala.util.Try[Assignment] =
    new AssignmentParser(ParserInput(input)).InputLine.run()
}

final case class Assignment(name: String, value: Int)

final case class IdQuery(resource: String, ids: Seq[Int])

final case class RgbColor(red: Int, green: Int, blue: Int)

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

final class Base64RuleParser(val input: ParserInput) extends Parser with Base64Parsing

final class QuotedTextParser(val input: ParserInput) extends Parser with StringBuilding {
  def QuotedText: Rule1[String] = rule {
    '"' ~ clearSB() ~ zeroOrMore(EscapedChar | PlainChar) ~ '"' ~ EOI ~ push(sb.toString)
  }

  def EscapedChar: Rule0 = rule {
    '\\' ~ ('n' ~ appendSB('\n') | 't' ~ appendSB('\t') | '"' ~ appendSB('"') | '\\' ~ appendSB('\\'))
  }

  def PlainChar: Rule0 = rule { noneOf("\"\\") ~ appendSB() }
}

final class IdQueryParser(val input: ParserInput) extends Parser {
  def Query: Rule1[IdQuery] = rule { Resource ~ "?ids=" ~ Ids ~ EOI ~> ((resource: String, ids: Seq[Int]) => IdQuery(resource, ids)) }

  def Resource: Rule1[String] = rule { capture(oneOrMore(CharPredicate.AlphaNum ++ '_')) }

  def Ids: Rule1[Seq[Int]] = rule { oneOrMore(Id).separatedBy(',') }

  def Id: Rule1[Int] = rule { capture(oneOrMore(CharPredicate.Digit)) ~> ((digits: String) => digits.toInt) }
}

final class HexColorParser(val input: ParserInput) extends Parser {
  def HexColor: Rule1[RgbColor] = rule {
    '#' ~ HexByte ~ HexByte ~ HexByte ~ EOI ~> ((red: Int, green: Int, blue: Int) => RgbColor(red, green, blue))
  }

  def HexByte: Rule1[Int] = rule { capture(2.times(HexDigit)) ~> ((digits: String) => Integer.parseInt(digits, 16)) }

  def HexDigit: Rule0 = rule { CharPredicate.HexDigit }
}

final class LookaheadDirectiveParser(val input: ParserInput) extends Parser {
  def Directive: Rule1[String] = rule { &("token:") ~ "token:" ~ !ReservedIdentifier ~ Identifier ~ EOI }

  def ReservedIdentifier: Rule0 = rule { ignoreCase("debug") ~ &(EOI) }

  def Identifier: Rule1[String] = rule { capture(CharPredicate.Alpha ~ zeroOrMore(CharPredicate.AlphaNum ++ '-')) }
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

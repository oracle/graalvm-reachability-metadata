/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.fastparse_3

import java.io.StringReader

import fastparse.*
import fastparse.IndexedParserInput
import fastparse.IteratorParserInput
import fastparse.ReaderParserInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class Fastparse_3Test {
  @Test
  def parsesExpressionLanguageWithCapturesRepetitionAndEndChecking(): Unit = {
    val parsed: Parsed[Assignment] = parse("answer = 1 + 2 * (3 + 4) - -5", ExpressionGrammar.assignment(_))

    assertThat(parsed).isEqualTo(Parsed.Success(Assignment("answer", 20), 29))
  }

  @Test
  def reportsStructuredFailuresAndTraceMessagesForInvalidExpressions(): Unit = {
    val parsed: Parsed[Assignment] = parse("answer = 1 + * 2", ExpressionGrammar.assignment(_), verboseFailures = true)
    val failure: Parsed.Failure = expectFailure(parsed)
    val traced: Parsed.TracedFailure = failure.trace()

    assertThat(failure.index).isEqualTo(11)
    assertThat(failure.msg).contains("Expected")
    assertThat(failure.msg).contains("*")
    assertThat(failure.longMsg).contains("assignment")
    assertThat(traced.terminalsMsg).contains("Expected")
    assertThat(traced.aggregateMsg).contains("Expected")
    assertThat(traced.longAggregateMsg).contains("Expected")
  }

  @Test
  def parsesDelimitedRecordsFromChunkedIteratorInput(): Unit = {
    val inspectedInput: IteratorParserInput = IteratorParserInput(Iterator("alpha,be", "ta,gamma", ",delta"))
    val parsedInput: IteratorParserInput = IteratorParserInput(Iterator("alpha,be", "ta,gamma", ",delta"))
    val parsed: Parsed[Seq[String]] = parse(parsedInput, CsvGrammar.values(_))

    assertThat(inspectedInput.isReachable(21)).isTrue
    assertThat(inspectedInput.slice(11, 16)).isEqualTo("gamma")
    assertThat(parsed).isEqualTo(Parsed.Success(Seq("alpha", "beta", "gamma", "delta"), 22))
  }

  @Test
  def exposesIndexedParserInputSlicesPositionsAndFoldedResults(): Unit = {
    val input: IndexedParserInput = IndexedParserInput("first\nsecond\nthird")
    val parsed: Parsed[SpanToken] = parse(input, SpanGrammar.secondLineToken(_))
    val folded: String = parsed.fold(
      (label: String, index: Int, extra: Parsed.Extra) => s"failure:$label@$index:${extra.startIndex}",
      (token: SpanToken, index: Int) => s"${token.text}:${token.start}-${token.end}:$index"
    )

    assertThat(input.length).isEqualTo(18)
    assertThat(input.innerLength).isEqualTo(18)
    assertThat(input(6)).isEqualTo('s')
    assertThat(input.slice(6, 12)).isEqualTo("second")
    assertThat(input.prettyIndex(6)).isEqualTo("2:1")
    assertThat(folded).isEqualTo("second:6-12:18")
  }

  @Test
  def handlesWhitespaceModesIncludingNestedAndUnclosedComments(): Unit = {
    val scalaParsed: Parsed[Seq[String]] = parse(
      "alpha /* outer /* nested */ done */ , // line comment\n beta",
      WhitespaceGrammars.scalaIdentifierList(_)
    )
    val javaFailure: Parsed.Failure = expectFailure(
      parse("left /* missing close */ right /* unclosed", WhitespaceGrammars.javaIdentifierPair(_), verboseFailures = true)
    )

    assertThat(scalaParsed).isEqualTo(Parsed.Success(Seq("alpha", "beta"), 59))
    assertThat(javaFailure.msg).contains("*/")
    assertThat(javaFailure.index).isEqualTo(42)
  }

  @Test
  def usesCaseInsensitiveAlternativesLookaheadPredicatesAndValidation(): Unit = {
    val command: Parsed[String] = parse("SeLeCt", CommandGrammar.command(_))
    val identifier: Parsed[String] = parse("customer_42", CommandGrammar.nonKeywordIdentifier(_))
    val keywordAsIdentifier: Parsed[String] = parse("DELETE", CommandGrammar.nonKeywordIdentifier(_), verboseFailures = true)
    val evenHalf: Parsed[Int] = parse("42", CommandGrammar.evenHalf(_))
    val oddHalf: Parsed[Int] = parse("41", CommandGrammar.evenHalf(_), verboseFailures = true)

    assertThat(command).isEqualTo(Parsed.Success("select", 6))
    assertThat(identifier).isEqualTo(Parsed.Success("customer_42", 11))
    assertThat(expectFailure(keywordAsIdentifier).msg).contains("non-keyword identifier")
    assertThat(evenHalf).isEqualTo(Parsed.Success(21, 2))
    assertThat(expectFailure(oddHalf).msg).contains("Expected")
  }

  @Test
  def synthesizesDefaultValuesAndExplicitFailuresWithPassAndFail(): Unit = {
    val defaulted: Parsed[Directive] = parse("cache", DirectiveGrammar.directive(_))
    val enabled: Parsed[Directive] = parse("metrics=true", DirectiveGrammar.directive(_))
    val invalidBoolean: Parsed.Failure = expectFailure(
      parse("maybe", DirectiveGrammar.booleanValue(_), verboseFailures = true)
    )

    assertThat(defaulted).isEqualTo(Parsed.Success(Directive("cache", false), 5))
    assertThat(enabled).isEqualTo(Parsed.Success(Directive("metrics", true), 12))
    assertThat(invalidBoolean.msg).contains("boolean literal")
  }

  @Test
  def demonstratesCutBacktrackingAndNoCutRecovery(): Unit = {
    val noCut: Parsed[Unit] = parse("ac", CutGrammar.withoutCut(_))
    val cutFailure: Parsed.Failure = expectFailure(parse("ac", CutGrammar.withCut(_), verboseFailures = true))
    val recovered: Parsed[Unit] = parse("ac", CutGrammar.cutWrappedInNoCut(_))

    assertThat(noCut).isEqualTo(Parsed.Success((), 2))
    assertThat(cutFailure.index).isEqualTo(1)
    assertThat(cutFailure.msg).contains("b")
    assertThat(recovered).isEqualTo(Parsed.Success((), 2))
  }

  @Test
  def supportsDependentParsingWithFlatMapAndRawSequencing(): Unit = {
    assertThat(parse("'fastparse'", QuotedGrammar.quoted(_))).isEqualTo(Parsed.Success("fastparse", 11))
    assertThat(parse("\"Scala 3\"", QuotedGrammar.quoted(_))).isEqualTo(Parsed.Success("Scala 3", 9))

    val failure: Parsed.Failure = expectFailure(parse("'unterminated", QuotedGrammar.quoted(_), verboseFailures = true))
    assertThat(failure.msg).contains("Expected")
  }

  @Test
  def parsesUsingCharacterPredicatesAndAnyCharacterParsers(): Unit = {
    val parsed: Parsed[CharacterSummary] = parse("Aλ9!?", CharacterGrammar.summary(_))

    assertThat(parsed).isEqualTo(Parsed.Success(CharacterSummary("Aλ", "9", "!?"), 5))
  }

  @Test
  def parsesReaderBackedInputWhileBufferingAcrossReads(): Unit = {
    val inputText: String = "name\t=  fastparse"
    val inspectedReader: StringReader = new StringReader(inputText)
    val parsedReader: StringReader = new StringReader(inputText)

    try {
      val inspectedInput: ReaderParserInput = ReaderParserInput(inspectedReader, 4)
      val parsedInput: ReaderParserInput = ReaderParserInput(parsedReader, 4)
      val parsed: Parsed[(String, String)] = parse(parsedInput, ReaderBackedGrammar.assignment(_))

      assertThat(inspectedInput.isReachable(16)).isTrue
      assertThat(inspectedInput.slice(0, 4)).isEqualTo("name")
      assertThat(parsed).isEqualTo(Parsed.Success(("name", "fastparse"), 17))
    } finally {
      inspectedReader.close()
      parsedReader.close()
    }
  }

  private def expectFailure[T](parsed: Parsed[T]): Parsed.Failure = parsed match {
    case failure: Parsed.Failure => failure
    case success => fail(s"Expected parse failure, got $success")
  }
}

final case class Assignment(name: String, value: Int)

final case class SpanToken(text: String, start: Int, end: Int)

final case class CharacterSummary(letters: String, digits: String, punctuation: String)

final case class Directive(name: String, enabled: Boolean)

private object ExpressionGrammar {
  import fastparse.*
  import fastparse.MultiLineWhitespace.*

  def assignment[$: P]: P[Assignment] = P(identifier ~ "=" ~/ expression ~ End).map {
    case (name: String, value: Int) => Assignment(name, value)
  }

  def expression[$: P]: P[Int] = P(term ~ (CharIn("+\\-").! ~ term).rep).map {
    case (first: Int, rest: Seq[(String, Int)]) =>
      rest.foldLeft(first) {
        case (total: Int, ("+", value: Int)) => total + value
        case (total: Int, ("-", value: Int)) => total - value
        case (total: Int, (operator: String, _: Int)) => fail(s"Unexpected operator $operator")
      }
  }

  def term[$: P]: P[Int] = P(factor ~ (CharIn("*", "/").! ~ factor).rep).map {
    case (first: Int, rest: Seq[(String, Int)]) =>
      rest.foldLeft(first) {
        case (total: Int, ("*", value: Int)) => total * value
        case (total: Int, ("/", value: Int)) => total / value
        case (total: Int, (operator: String, _: Int)) => fail(s"Unexpected operator $operator")
      }
  }

  def factor[$: P]: P[Int] = P(number | "(" ~/ expression ~ ")")

  def number[$: P]: P[Int] = P(CharIn("+\\-").!.? ~~ CharsWhileIn("0-9").!).map {
    case (Some("-"), digits: String) => -digits.toInt
    case (_, digits: String) => digits.toInt
  }

  def identifier[$: P]: P[String] = P((CharIn("a-z", "A-Z", "_") ~~ CharsWhileIn("a-zA-Z0-9_", 0)).!)
}

private object CsvGrammar {
  import fastparse.*
  import fastparse.NoWhitespace.*

  def values[$: P]: P[Seq[String]] = P(value.rep(min = 1, sep = ",") ~ End)

  def value[$: P]: P[String] = P(CharsWhileIn("a-zA-Z", min = 1).!)
}

private object SpanGrammar {
  import fastparse.*
  import fastparse.MultiLineWhitespace.*

  def secondLineToken[$: P]: P[SpanToken] = P("first" ~ Index ~ word ~~ Index ~ "third" ~ End).map {
    case (start: Int, text: String, end: Int) => SpanToken(text, start, end)
  }

  def word[$: P]: P[String] = P(CharsWhileIn("a-zA-Z").!)
}

private object WhitespaceGrammars {
  import fastparse.*

  def scalaIdentifierList[$: P]: P[Seq[String]] = {
    import fastparse.ScalaWhitespace.*
    P(identifier.rep(min = 1, sep = ",") ~ End)
  }

  def javaIdentifierPair[$: P]: P[(String, String)] = {
    import fastparse.JavaWhitespace.*
    P(identifier ~ identifier ~ End)
  }

  private def identifier[$: P]: P[String] = P(CharsWhileIn("a-zA-Z", min = 1).!)
}

private object CommandGrammar {
  import fastparse.*
  import fastparse.NoWhitespace.*

  def command[$: P]: P[String] = P(&(keyword) ~ keyword ~ End).map(_.toLowerCase)

  def nonKeywordIdentifier[$: P]: P[String] = P((!reservedWord ~ identifier).opaque("non-keyword identifier") ~ End)

  def evenHalf[$: P]: P[Int] = P(ExpressionGrammar.number.filter(_ % 2 == 0).map(_ / 2) ~ End)

  private def keyword[$: P]: P[String] = P(StringInIgnoreCase("select", "insert", "delete").!)

  private def reservedWord[$: P]: P[String] = P(keyword ~ End)

  private def identifier[$: P]: P[String] = P((CharIn("a-z", "A-Z", "_") ~~ CharsWhileIn("a-zA-Z0-9_", 0)).!)
}

private object DirectiveGrammar {
  import fastparse.*
  import fastparse.NoWhitespace.*

  def directive[$: P]: P[Directive] = P(name ~ ("=" ~ booleanLiteral | Pass(false)) ~ End).map {
    case (name: String, enabled: Boolean) => Directive(name, enabled)
  }

  def booleanValue[$: P]: P[Boolean] = P(booleanLiteral ~ End)

  private def booleanLiteral[$: P]: P[Boolean] = P(
    "true".!.map(_ => true) | "false".!.map(_ => false) | Fail("boolean literal")
  )

  private def name[$: P]: P[String] = P(CharsWhileIn("a-zA-Z", min = 1).!)
}

private object CutGrammar {
  import fastparse.*
  import fastparse.NoWhitespace.*

  def withoutCut[$: P]: P[Unit] = P(("a" ~ "b" | "ac") ~ End)

  def withCut[$: P]: P[Unit] = P(("a" ~/ "b" | "ac") ~ End)

  def cutWrappedInNoCut[$: P]: P[Unit] = P((NoCut("a" ~/ "b") | "ac") ~ End)
}

private object QuotedGrammar {
  import fastparse.*
  import fastparse.NoWhitespace.*

  def quoted[$: P]: P[String] = P(quote.flatMapX(parseBodyAndClosingQuote) ~ End)

  private def quote[$: P]: P[String] = P(CharIn("\"", "'").!)

  private def parseBodyAndClosingQuote[$: P](delimiter: String): P[String] = {
    val delimiterChar: Char = delimiter.charAt(0)
    P(CharsWhile((char: Char) => char != delimiterChar, min = 0).! ~~ CharPred((char: Char) => char == delimiterChar))
  }
}

private object CharacterGrammar {
  import fastparse.*
  import fastparse.NoWhitespace.*

  def summary[$: P]: P[CharacterSummary] = P(letters ~ digits ~ AnyChar.rep(exactly = 2).! ~ End).map {
    case (letters: String, digits: String, punctuation: String) => CharacterSummary(letters, digits, punctuation)
  }

  private def letters[$: P]: P[String] = P(CharsWhile(CharPredicates.isLetter, min = 1).!)

  private def digits[$: P]: P[String] = P(CharsWhile(CharPredicates.isDigit, min = 1).!)
}

private object ReaderBackedGrammar {
  import fastparse.*
  import fastparse.SingleLineWhitespace.*

  def assignment[$: P]: P[(String, String)] = P(identifier ~ "=" ~ identifier ~ End)

  private def identifier[$: P]: P[String] = P(CharsWhileIn("a-zA-Z", min = 1).!)
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang_modules.scala_parser_combinators_3

import java.io.StringReader

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import scala.jdk.CollectionConverters.*
import scala.util.parsing.combinator.JavaTokenParsers
import scala.util.parsing.combinator.PackratParsers
import scala.util.parsing.combinator.Parsers
import scala.util.parsing.combinator.RegexParsers
import scala.util.parsing.combinator.syntactical.StandardTokenParsers
import scala.util.parsing.input.CharArrayReader
import scala.util.parsing.input.CharSequenceReader
import scala.util.parsing.input.OffsetPosition
import scala.util.parsing.input.PagedSeq
import scala.util.parsing.input.PagedSeqReader
import scala.util.parsing.input.Position
import scala.util.parsing.input.Positional
import scala.util.parsing.input.Reader
import scala.util.parsing.input.StreamReader

class Scala_parser_combinators_3Test {
  @Test
  def regexParsersComposeRecursiveExpressionGrammarAndReportFailures(): Unit = {
    val result: ArithmeticParser.ParseResult[Int] = ArithmeticParser.parseAll(
      ArithmeticParser.expression,
      "2 + 3 * (4 + 5) - 6 / 2"
    )

    assertThat(result.successful).isTrue()
    assertThat(result.get).isEqualTo(26)

    val failure: ArithmeticParser.ParseResult[Int] = ArithmeticParser.parseAll(ArithmeticParser.expression, "1 +")
    val failureText: String = failure.toString
    assertThat(failure.successful).isFalse()
    assertThat(failureText).contains("failure")
    assertThat(failureText).contains("1 +")
  }

  @Test
  def javaTokenParsersReadStructuredKeyValueDocuments(): Unit = {
    val source: String = """
      host = "localhost";
      port = 5432;
      enabled = true;
      ratio = 0.75
      """

    val result: KeyValueParser.ParseResult[Map[String, Any]] = KeyValueParser.parseAll(KeyValueParser.document, source)

    assertThat(result.successful).isTrue()
    assertThat(result.get("host")).isEqualTo("localhost")
    assertThat(result.get("port")).isEqualTo(BigDecimal(5432))
    assertThat(result.get("enabled")).isEqualTo(true)
    assertThat(result.get("ratio")).isEqualTo(BigDecimal("0.75"))
  }

  @Test
  def positionedParsersAttachSourceLocationsToParsedValues(): Unit = {
    val source: String = "val answer = 42\nval next = 7"

    val result: DeclarationParser.ParseResult[List[Declaration]] = DeclarationParser.parseAll(
      DeclarationParser.document,
      source
    )

    assertThat(result.successful).isTrue()
    assertThat(result.get.map(_.name).asJava).containsExactly("answer", "next")
    assertThat(result.get.head.value).isEqualTo(42)
    assertThat(result.get.head.pos.line).isEqualTo(1)
    assertThat(result.get.head.pos.column).isEqualTo(1)
    assertThat(result.get(1).pos.line).isEqualTo(2)
    assertThat(result.get(1).pos.column).isEqualTo(1)
  }

  @Test
  def packratParsersHandleDirectLeftRecursionWithMemoizedInput(): Unit = {
    val result: LeftRecursiveExpressionParser.ParseResult[Int] = LeftRecursiveExpressionParser.parseAll(
      LeftRecursiveExpressionParser.expression,
      "1 + 2 + (3 + 4) + 5"
    )

    assertThat(result.successful).isTrue()
    assertThat(result.get).isEqualTo(15)
  }

  @Test
  def standardTokenParsersUseConfigurableLexicalScanner(): Unit = {
    val language: LetLanguage = new LetLanguage
    val source: String = """
      // StdLexical skips line comments before scanning tokens.
      let answer = 41;
      let extra = 1;
      print 1 + 2 + 3;
      """

    val result: language.ParseResult[(List[(String, Int)], Int)] = language.parseProgram(source)

    assertThat(result.successful).isTrue()
    assertThat(result.get._1.asJava).containsExactly("answer" -> 41, "extra" -> 1)
    assertThat(result.get._2).isEqualTo(6)
  }

  @Test
  def syntacticPredicatesPerformLookaheadWithoutConsumingInput(): Unit = {
    val guarded: LookaheadParser.ParseResult[String] = LookaheadParser.parseAll(
      LookaheadParser.casePrefixedIdentifier,
      "case"
    )
    val ordinaryIdentifier: LookaheadParser.ParseResult[String] = LookaheadParser.parseAll(
      LookaheadParser.nonReservedIdentifier,
      "caseValue"
    )
    val reservedWord: LookaheadParser.ParseResult[String] = LookaheadParser.parseAll(
      LookaheadParser.nonReservedIdentifier,
      "case"
    )

    assertThat(guarded.successful).isTrue()
    assertThat(guarded.get).isEqualTo("case")
    assertThat(ordinaryIdentifier.successful).isTrue()
    assertThat(ordinaryIdentifier.get).isEqualTo("caseValue")
    assertThat(reservedWord.successful).isFalse()
    assertThat(reservedWord.toString).contains("case")
  }

  @Test
  def chainCombinatorsApplyOperatorAssociativity(): Unit = {
    val leftAssociative: OperatorChainParser.ParseResult[Int] = OperatorChainParser.parseAll(
      OperatorChainParser.leftAssociativeSubtraction,
      "20 - 5 - 3"
    )
    val rightAssociative: OperatorChainParser.ParseResult[Int] = OperatorChainParser.parseAll(
      OperatorChainParser.rightAssociativeExponentiation,
      "2 ^ 3 ^ 2"
    )

    assertThat(leftAssociative.successful).isTrue()
    assertThat(leftAssociative.get).isEqualTo(12)
    assertThat(rightAssociative.successful).isTrue()
    assertThat(rightAssociative.get).isEqualTo(512)
  }

  @Test
  def parserAlternativesOptionsAndSeparatorsProduceExpectedCollections(): Unit = {
    val withTag: CommandParser.ParseResult[Command] = CommandParser.parseAll(
      CommandParser.command,
      "deploy api, worker as production"
    )
    val withoutTag: CommandParser.ParseResult[Command] = CommandParser.parseAll(
      CommandParser.command,
      "test smoke"
    )
    val invalid: CommandParser.ParseResult[Command] = CommandParser.parseAll(CommandParser.command, "deploy as production")

    assertThat(withTag.successful).isTrue()
    assertThat(withTag.get.action).isEqualTo("deploy")
    assertThat(withTag.get.targets.asJava).containsExactly("api", "worker")
    assertThat(withTag.get.tag).isEqualTo(Some("production"))
    assertThat(withoutTag.successful).isTrue()
    assertThat(withoutTag.get).isEqualTo(Command("test", List("smoke"), None))
    assertThat(invalid.successful).isFalse()
  }

  @Test
  def coreParsersCanParseCustomTokenReaders(): Unit = {
    val result: WordTokenParser.ParseResult[(String, List[String])] = WordTokenParser.parseTokens(
      List("run", "alpha", ",", "beta", "!")
    )
    val failure: WordTokenParser.ParseResult[(String, List[String])] = WordTokenParser.parseTokens(
      List("run", "alpha", ",", "123")
    )

    assertThat(result.successful).isTrue()
    assertThat(result.get._1).isEqualTo("run")
    assertThat(result.get._2.asJava).containsExactly("alpha", "beta")
    assertThat(failure.successful).isFalse()
    assertThat(failure.toString).contains("identifier expected")
  }

  @Test
  def longestAlternativeCombinatorSelectsParserConsumingMostInput(): Unit = {
    val longestMatch: LongestAlternativeParser.ParseResult[String] = LongestAlternativeParser.parseAll(
      LongestAlternativeParser.longestKeyword,
      "selective"
    )
    val firstSuccessfulMatch: LongestAlternativeParser.ParseResult[String] = LongestAlternativeParser.parseAll(
      LongestAlternativeParser.firstKeyword,
      "selective"
    )

    assertThat(longestMatch.successful).isTrue()
    assertThat(longestMatch.get).isEqualTo("long")
    assertThat(firstSuccessfulMatch.successful).isFalse()
    assertThat(firstSuccessfulMatch.toString).contains("end of input")
  }

  @Test
  def characterReadersExposeContentOffsetsAndHumanReadablePositions(): Unit = {
    val sequenceReader: CharSequenceReader = new CharSequenceReader("alpha\nβeta")
    val secondLineReader: CharSequenceReader = sequenceReader.drop(6)

    assertThat(sequenceReader.first).isEqualTo('a')
    assertThat(secondLineReader.first).isEqualTo('β')
    assertThat(secondLineReader.pos.line).isEqualTo(2)
    assertThat(secondLineReader.pos.column).isEqualTo(1)

    val arrayReader: CharArrayReader = new CharArrayReader("xyz".toCharArray)
    assertThat(arrayReader.first).isEqualTo('x')
    assertThat(arrayReader.drop(2).first).isEqualTo('z')
    assertThat(arrayReader.drop(3).atEnd).isTrue()

    val position: OffsetPosition = OffsetPosition("first\nsecond", 8)
    assertThat(position.line).isEqualTo(2)
    assertThat(position.column).isEqualTo(3)
    assertThat(position.lineContents).isEqualTo("second")
    assertThat(position.longString).contains("second").contains("^")
  }

  @Test
  def pagedAndStreamReadersProvideReaderApiForIncrementalCharacterSources(): Unit = {
    val pagedReader: PagedSeqReader = new PagedSeqReader(PagedSeq.fromStrings(List("ab", "\ncd")))
    val pagedSecondLine: PagedSeqReader = pagedReader.drop(3)

    assertThat(pagedReader.first).isEqualTo('a')
    assertThat(pagedSecondLine.first).isEqualTo('c')
    assertThat(pagedSecondLine.pos.line).isEqualTo(2)
    assertThat(pagedSecondLine.pos.column).isEqualTo(1)

    val stringReader: StringReader = new StringReader("xy\nz")
    try {
      val streamReader: StreamReader = StreamReader(stringReader)
      val streamSecondLine: StreamReader = streamReader.drop(3)

      assertThat(streamReader.first).isEqualTo('x')
      assertThat(streamSecondLine.first).isEqualTo('z')
      assertThat(streamSecondLine.pos.line).isEqualTo(2)
      assertThat(streamSecondLine.pos.column).isEqualTo(1)
      assertThat(streamSecondLine.rest.atEnd).isTrue()
    } finally {
      stringReader.close()
    }
  }

  private object ArithmeticParser extends RegexParsers {
    override val skipWhitespace: Boolean = true

    def expression: Parser[Int] = term ~ rep(("+" | "-") ~ term) ^^ {
      case first ~ operations =>
        operations.foldLeft(first) {
          case (left, "+" ~ right) => left + right
          case (left, "-" ~ right) => left - right
        }
    }

    private def term: Parser[Int] = factor ~ rep(("*" | "/") ~ factor) ^^ {
      case first ~ operations =>
        operations.foldLeft(first) {
          case (left, "*" ~ right) => left * right
          case (left, "/" ~ right) => left / right
        }
    }

    private def factor: Parser[Int] = number | "(" ~> expression <~ ")"

    private def number: Parser[Int] = """-?\d+""".r ^^ (_.toInt)
  }

  private object KeyValueParser extends JavaTokenParsers {
    def document: Parser[Map[String, Any]] = phrase(repsep(entry, ";")) ^^ (_.toMap)

    private def entry: Parser[(String, Any)] = ident ~ ("=" ~> value) ^^ {
      case key ~ parsedValue => key -> parsedValue
    }

    private def value: Parser[Any] =
      stringLiteral ^^ unquote |
        floatingPointNumber ^^ (number => BigDecimal(number)) |
        ("true" | "false") ^^ (_.toBoolean)

    private def unquote(text: String): String = text.substring(1, text.length - 1)
  }

  private object LookaheadParser extends RegexParsers {
    override val skipWhitespace: Boolean = true

    def nonReservedIdentifier: Parser[String] = not(caseKeyword) ~> identifier

    def casePrefixedIdentifier: Parser[String] = guard(caseKeyword) ~> identifier

    private def identifier: Parser[String] = """[A-Za-z_][A-Za-z0-9_]*""".r

    private def caseKeyword: Parser[String] = """case(?![A-Za-z0-9_])""".r
  }

  private object OperatorChainParser extends RegexParsers {
    override val skipWhitespace: Boolean = true

    private val subtract: (Int, Int) => Int = (left: Int, right: Int) => left - right
    private val exponentiate: (Int, Int) => Int = (base: Int, exponent: Int) => BigInt(base).pow(exponent).toInt

    def leftAssociativeSubtraction: Parser[Int] = chainl1(integer, "-" ^^^ subtract)

    def rightAssociativeExponentiation: Parser[Int] = chainr1(integer, "^" ^^^ exponentiate, exponentiate, 1)

    private def integer: Parser[Int] = """\d+""".r ^^ (_.toInt)
  }

  private final case class Command(action: String, targets: List[String], tag: Option[String])

  private object CommandParser extends RegexParsers {
    override protected val whiteSpace: scala.util.matching.Regex = """(\s|#[^\n]*)+""".r

    def command: Parser[Command] = phrase(("deploy" | "test") ~ rep1sep(identifier, ",") ~ opt("as" ~> identifier)) ^^ {
      case action ~ targets ~ tag => Command(action, targets, tag)
    }

    private def identifier: Parser[String] = """[A-Za-z][A-Za-z0-9_-]*""".r
  }

  private final case class Declaration(name: String, value: Int) extends Positional

  private object DeclarationParser extends JavaTokenParsers {
    def document: Parser[List[Declaration]] = phrase(rep1(declaration))

    private def declaration: Parser[Declaration] = positioned(
      "val" ~> ident ~ ("=" ~> wholeNumber) ^^ {
        case name ~ value => Declaration(name, value.toInt)
      }
    )
  }

  private object LeftRecursiveExpressionParser extends JavaTokenParsers with PackratParsers {
    lazy val expression: PackratParser[Int] =
      expression ~ "+" ~ atom ^^ {
        case left ~ _ ~ right => left + right
      } |
        atom

    private lazy val atom: PackratParser[Int] = wholeNumber ^^ (_.toInt) | "(" ~> expression <~ ")"
  }

  private final class LetLanguage extends StandardTokenParsers {
    lexical.reserved ++= List("let", "print")
    lexical.delimiters ++= List("=", ";", "+")

    def parseProgram(source: String): ParseResult[(List[(String, Int)], Int)] = phrase(program)(new lexical.Scanner(source))

    private def program: Parser[(List[(String, Int)], Int)] = rep1(assignment) ~ ("print" ~> addition <~ ";") ^^ {
      case assignments ~ printedValue => assignments -> printedValue
    }

    private def assignment: Parser[(String, Int)] = "let" ~> ident ~ ("=" ~> numericLit) <~ ";" ^^ {
      case name ~ value => name -> value.toInt
    }

    private def addition: Parser[Int] = numericLit ~ rep("+" ~> numericLit) ^^ {
      case first ~ rest => (first +: rest).map(_.toInt).sum
    }
  }

  private object WordTokenParser extends Parsers {
    override type Elem = String

    def parseTokens(tokens: List[String]): ParseResult[(String, List[String])] = phrase(command)(TokenReader(tokens.toIndexedSeq))

    private def command: Parser[(String, List[String])] = action ~ rep1sep(identifier, accept(",")) <~ opt(accept("!")) ^^ {
      case parsedAction ~ names => parsedAction -> names
    }

    private def action: Parser[String] = accept("run") | accept("test")

    private def identifier: Parser[String] = acceptIf(_.matches("[A-Za-z][A-Za-z0-9_-]*")) { token =>
      s"identifier expected instead of $token"
    }
  }

  private object LongestAlternativeParser extends RegexParsers {
    override val skipWhitespace: Boolean = false

    def longestKeyword: Parser[String] = ("select" ^^^ "short") ||| ("selective" ^^^ "long")

    def firstKeyword: Parser[String] = ("select" ^^^ "short") | ("selective" ^^^ "long")
  }

  private final case class TokenReader(tokens: IndexedSeq[String], override val offset: Int = 0) extends Reader[String] {
    override def first: String = if atEnd then "" else tokens(offset)

    override def rest: Reader[String] = if atEnd then this else copy(offset = offset + 1)

    override def pos: Position = TokenPosition(tokens, offset)

    override def atEnd: Boolean = offset >= tokens.length
  }

  private final case class TokenPosition(tokens: IndexedSeq[String], offset: Int) extends Position {
    override def line: Int = 1

    override def column: Int = offset + 1

    override def lineContents: String = tokens.mkString(" ")
  }
}

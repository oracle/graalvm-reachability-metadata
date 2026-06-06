/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.scalameta_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.collection.mutable
import scala.meta._
import scala.meta.contrib.AssociatedComments
import scala.meta.parsers.Parsed
import scala.meta.transversers.Transformer
import scala.meta.transversers.Traverser

class Scalameta_2_13Test {
  @Test
  def parseSourceAndInspectTreeShape(): Unit = {
    val input: Input.VirtualFile = Input.VirtualFile(
      "demo/Repository.scala",
      """
        |package demo
        |
        |case class User(id: Long, name: String)
        |
        |object Repository {
        |  def activeNames(users: List[User]): List[String] =
        |    users.collect { case User(_, name) if name.nonEmpty => name }
        |}
        |""".stripMargin
    )

    val source: Source = parse(input.parse[Source])

    source match {
      case Source(List(Pkg(ref, stats))) =>
        assertEquals("demo", ref.syntax)
        assertEquals(2, stats.size)
        assertTrue(stats.head.is[Defn.Class])
        assertTrue(stats(1).is[Defn.Object])
      case other =>
        throw new AssertionError(s"Unexpected source shape: ${other.structure}")
    }
    assertTrue(source.syntax.contains("case class User"))
    assertTrue(source.structure.contains("Defn.Class"))
    assertEquals(input, source.pos.input)
  }

  @Test
  def tokenizeInputAndReportStablePositions(): Unit = {
    val input: Input.VirtualFile = Input.VirtualFile(
      "Coordinates.scala",
      "object Coordinates {\n  val answer = 42 // documented\n}\n"
    )

    val tokens: Tokens = input.tokenize.fold(
      error => throw new AssertionError(error.toString),
      tokens => tokens
    )
    val tokenTexts: Seq[String] = tokens.map(_.text)

    assertTrue(tokenTexts.contains("object"))
    assertTrue(tokenTexts.contains("Coordinates"))
    assertTrue(tokenTexts.contains("// documented"))

    val answerToken: Token = tokens.find(_.text == "answer").getOrElse {
      throw new AssertionError(s"Could not find answer token in ${tokens.structure}")
    }
    assertEquals(1, answerToken.pos.startLine)
    assertEquals(6, answerToken.pos.startColumn)
    assertEquals(1, answerToken.pos.endLine)
    assertEquals(12, answerToken.pos.endColumn)
  }

  @Test
  def parseTermsTypesPatternsAndStats(): Unit = {
    val term: Term = parse("users.map(_.name).filter(_.nonEmpty)".parse[Term])
    val tpe: Type = parse("Either[String, List[Int]]".parse[Type])
    val pattern: Pat = parse("User(_, name)".parse[Pat])
    val stat: Stat = parse("private final val answer: Int = 42".parse[Stat])

    assertEquals("users.map(_.name).filter(_.nonEmpty)", term.syntax)
    assertTrue(term.structure.contains("Term.Apply"))
    assertEquals("Either[String, List[Int]]", tpe.syntax)
    assertTrue(tpe.structure.contains("Type.Apply"))
    assertEquals("User(_, name)", pattern.syntax)
    assertTrue(pattern.structure.contains("Pat.Extract"))
    assertEquals("private final val answer: Int = 42", stat.syntax)
    assertTrue(stat.is[Defn.Val])
  }

  @Test
  def traverseTreeAndTransformSelectedTerms(): Unit = {
    val source: Source = parse(
      """
        |object MathHelpers {
        |  val incremented = input + 1
        |  val rendered = input.toString
        |}
        |""".stripMargin.parse[Source]
    )
    val seenNames: mutable.ListBuffer[String] = mutable.ListBuffer.empty[String]
    val integerLiterals: mutable.ListBuffer[Int] = mutable.ListBuffer.empty[Int]

    val traverser: Traverser = new Traverser {
      override def apply(tree: Tree): Unit = {
        tree match {
          case name: Name => seenNames += name.value
          case Lit.Int(value) => integerLiterals += value
          case _ =>
        }
        super.apply(tree)
      }
    }
    traverser.apply(source)

    assertTrue(seenNames.contains("MathHelpers"))
    assertTrue(seenNames.contains("incremented"))
    assertTrue(seenNames.count(_ == "input") >= 2)
    assertEquals(List(1), integerLiterals.toList)

    val transformer: Transformer = new Transformer {
      override def apply(tree: Tree): Tree = tree match {
        case Term.Name("input") => Term.Name("value")
        case _ => super.apply(tree)
      }
    }
    val rewritten: Tree = transformer.apply(source)

    assertTrue(rewritten.syntax.contains("value + 1"))
    assertTrue(rewritten.syntax.contains("value.toString"))
    assertFalse(rewritten.syntax.contains("input"))
  }

  @Test
  def buildTreesWithQuasiquotesAndCompareSyntax(): Unit = {
    val method: Defn.Def = q"def double(n: Int): Int = n * 2"
    val cls: Defn.Class = q"case class Box[A](value: A)"
    val term: Term = q"List(1, 2, 3).map(double)"
    val tpe: Type = t"Either[String, Box[Int]]"
    val pattern: Pat = p"Box(value)"

    assertEquals("double", method.name.value)
    assertEquals("def double(n: Int): Int = n * 2", method.syntax)
    assertEquals("Box", cls.name.value)
    assertTrue(cls.mods.exists(_.is[Mod.Case]))
    assertEquals("List(1, 2, 3).map(double)", term.syntax)
    assertEquals("Either[String, Box[Int]]", tpe.syntax)
    assertEquals("Box(value)", pattern.syntax)
  }

  @Test
  def respectDialectSpecificSyntax(): Unit = {
    val scala3Code: String =
      """
        |enum Color:
        |  case Red, Green, Blue
        |""".stripMargin

    val scala3Source: Source = parse(dialects.Scala3(scala3Code).parse[Source])
    val scala213Result: Parsed[Source] = dialects.Scala213(scala3Code).parse[Source]

    assertTrue(scala3Source.syntax.contains("enum Color"))
    assertTrue(scala3Source.structure.contains("Defn.Enum"))
    assertTrue(scala213Result.isInstanceOf[Parsed.Error])
  }

  @Test
  def tokenizeScaladocAndOrdinaryComments(): Unit = {
    val input: Input.VirtualFile = Input.VirtualFile(
      "Greeter.scala",
      """
        |/** Creates greeting text.
        |  *
        |  * @param name user name
        |  * @return greeting text
        |  */
        |object Greeter {
        |  def greet(name: String): String = s"Hello, $name"
        |}
        |
        |// ordinary implementation note
        |""".stripMargin
    )

    val tokens: Tokens = input.tokenize.fold(
      error => throw new AssertionError(error.toString),
      tokens => tokens
    )
    val comments: List[Token.Comment] = tokens.collect { case comment: Token.Comment => comment }.toList
    val scaladocComment: Token.Comment = comments.find(_.text.startsWith("/**")).getOrElse {
      throw new AssertionError(s"Could not find Scaladoc comment in ${tokens.structure}")
    }
    val ordinaryComment: Token.Comment = comments.find(_.text.startsWith("//")).getOrElse {
      throw new AssertionError(s"Could not find ordinary comment in ${tokens.structure}")
    }

    assertTrue(scaladocComment.text.contains("Creates greeting text."))
    assertTrue(scaladocComment.text.contains("@param name user name"))
    assertTrue(scaladocComment.text.contains("@return greeting text"))
    assertEquals(1, scaladocComment.pos.startLine)
    assertEquals(5, scaladocComment.pos.endLine)

    assertEquals("// ordinary implementation note", ordinaryComment.text)
    assertEquals(10, ordinaryComment.pos.startLine)
    assertEquals(10, ordinaryComment.pos.endLine)
  }

  @Test
  def associateLeadingAndTrailingCommentsWithDefinitions(): Unit = {
    val input: Input.VirtualFile = Input.VirtualFile(
      "Comments.scala",
      """
        |object Comments {
        |  // documents size calculation
        |  val size = users.size // cached expression
        |  val empty = users.isEmpty
        |}
        |""".stripMargin
    )
    val source: Source = parse(input.parse[Source])
    val associatedComments: AssociatedComments = AssociatedComments(source)
    val values: List[Defn.Val] = source match {
      case Source(List(obj: Defn.Object)) =>
        obj.templ.stats.collect { case value: Defn.Val => value }
      case other =>
        throw new AssertionError(s"Unexpected source shape: ${other.structure}")
    }
    val size: Defn.Val = values.find(_.pats.exists(_.syntax == "size")).getOrElse {
      throw new AssertionError(s"Could not find size definition in ${source.structure}")
    }
    val empty: Defn.Val = values.find(_.pats.exists(_.syntax == "empty")).getOrElse {
      throw new AssertionError(s"Could not find empty definition in ${source.structure}")
    }

    assertEquals(Set("// documents size calculation"), associatedComments.leading(size).map(_.text))
    assertEquals(Set("// cached expression"), associatedComments.trailing(size).map(_.text))
    assertTrue(associatedComments.hasComment(size))
    assertTrue(associatedComments.leading(empty).isEmpty)
    assertTrue(associatedComments.trailing(empty).isEmpty)
    assertFalse(associatedComments.hasComment(empty))
  }

  @Test
  def preserveInputRangesThroughParsingAndPrettyPrinting(): Unit = {
    val input: Input.VirtualFile = Input.VirtualFile(
      "Ranges.scala",
      """
        |object Ranges {
        |  def sum(values: List[Int]): Int = values.foldLeft(0)(_ + _)
        |}
        |""".stripMargin
    )
    val source: Source = parse(input.parse[Source])
    val definitions: mutable.ListBuffer[Defn.Def] = mutable.ListBuffer.empty[Defn.Def]

    new Traverser {
      override def apply(tree: Tree): Unit = {
        tree match {
          case definition: Defn.Def => definitions += definition
          case _ =>
        }
        super.apply(tree)
      }
    }.apply(source)

    val sum: Defn.Def = definitions.find(_.name.value == "sum").getOrElse {
      throw new AssertionError(s"Could not find sum method in ${source.structure}")
    }

    assertEquals("sum", sum.name.syntax)
    assertEquals("def sum(values: List[Int]): Int = values.foldLeft(0)(_ + _)", sum.syntax)
    assertEquals(2, sum.pos.startLine)
    assertEquals(2, sum.name.pos.startLine)
    assertTrue(sum.pos.startColumn < sum.name.pos.startColumn)
    assertEquals("Ranges.scala", input.path)
  }

  private def parse[A](result: Parsed[A]): A = result.fold(
    error => throw new AssertionError(error.toString),
    value => value
  )
}

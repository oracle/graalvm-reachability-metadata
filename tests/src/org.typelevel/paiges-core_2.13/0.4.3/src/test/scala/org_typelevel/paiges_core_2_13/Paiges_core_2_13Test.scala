/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.paiges_core_2_13

import java.io.PrintWriter
import java.io.StringWriter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.typelevel.paiges.Doc
import org.typelevel.paiges.Document
import org.typelevel.paiges.Style

class Paiges_core_2_13Test {
  @Test
  def rendersBasicTextCompositionAndOperators(): Unit = {
    val document: Doc =
      ("prefix: " +: Doc.text("hello") :+ "!")
        .space("world")
        .line(Doc.char('λ'))
        .line(Doc.str(42))

    assertEquals("prefix: hello! world\nλ\n42", document.render(80))
    assertEquals("prefix: hello! world\nλ\n42", document.renderWideStream.mkString)
    assertEquals("Doc(...)" , document.toString)
    assertTrue(document.nonEmpty)
    assertFalse(document.isEmpty)
    assertTrue(Doc.empty.isEmpty)
  }

  @Test
  def groupsFillAndLineAlternativesRespectAvailableWidth(): Unit = {
    val numbers: List[Doc] = List("1", "2", "3").map(Doc.text)
    val filled: Doc = Doc.fill(Doc.comma + Doc.line, numbers)

    assertEquals("1,\n2,\n3", filled.render(0))
    assertEquals("1, 2,\n3", filled.render(6))
    assertEquals("1, 2, 3", filled.render(10))

    val grouped: Doc = Doc.text("alpha").lineOrSpace("beta").lineOrSpace("gamma")
    assertEquals("alpha beta gamma", grouped.render(80))
    assertEquals("alpha\nbeta\ngamma", grouped.render(5))

    val semicolonSeparated: Doc = Doc.text("a") + Doc.lineOr(Doc.text("; ")) + Doc.text("b")
    assertEquals("a; b", semicolonSeparated.render(80))
    assertEquals("a\nb", semicolonSeparated.render(1))
  }

  @Test
  def bracketsNestAndAlignMultilineContent(): Unit = {
    val body: Doc = Doc.stack(List(Doc.text("red"), Doc.text("blue")))

    assertEquals("[ red blue ]", body.bracketBy(Doc.char('['), Doc.char(']')).render(80))
    assertEquals("[\n  red\n  blue\n]", body.bracketBy(Doc.char('['), Doc.char(']')).render(4))

    assertEquals("[red blue]", body.tightBracketBy(Doc.char('['), Doc.char(']')).render(80))
    assertEquals("[\n  red\n  blue\n]", body.tightBracketBy(Doc.char('['), Doc.char(']')).render(4))

    val aligned: Doc = Doc.text("foo") + (Doc.text("bar").line(Doc.text("baz"))).aligned
    assertEquals("foobar\n   baz", aligned.render(80))

    val hanging: Doc = Doc.split("this is an example").hang(2)
    assertEquals("this\n  is\n  an\n  example", hanging.render(4))
  }

  @Test
  def splitsParagraphsAndTables(): Unit = {
    val paragraph: Doc = Doc.paragraph("alpha beta gamma")
    assertEquals("alpha beta gamma", paragraph.render(80))
    assertEquals("alpha\nbeta\ngamma", paragraph.render(5))

    val customSplit: Doc = Doc.split("alpha,beta,gamma", ",".r, Doc.lineOr(Doc.text(" | ")))
    assertEquals("alpha | beta | gamma", customSplit.render(80))
    assertEquals("alpha\nbeta\ngamma", customSplit.render(5))

    val table: Doc = Doc.tabulate('.', " : ", List(
      "a" -> Doc.text("one"),
      "long" -> Doc.text("two")
    ))
    assertEquals("a... : one\nlong : two", table.render(80))

    val defaultTable: Doc = Doc.tabulate(List(
      "x" -> Doc.text("1"),
      "yy" -> Doc.text("2")
    ))
    assertEquals("x 1\nyy2", defaultTable.render(80))
  }

  @Test
  def concatenatesIntercalatesStacksAndSpreadsCollections(): Unit = {
    val words: List[Doc] = List("a", "b", "c").map(Doc.text)

    assertEquals("abc", Doc.cat(words).render(80))
    assertEquals("a b c", Doc.spread(words).render(80))
    assertEquals("a\nb\nc", Doc.stack(words).render(80))
    assertEquals("a, b, c", Doc.intercalate(Doc.comma + Doc.space, words).render(80))
    assertEquals("abc", Doc.foldDocs(words)(_ + _).render(80))
    assertEquals("", Doc.foldDocs(List.empty[Doc])(_ + _).render(80))
  }

  @Test
  def trimsIndentedBlankLinesAndWritesRenderedOutput(): Unit = {
    val document: Doc = Doc.text("head") / Doc.empty.indent(4) / Doc.text("tail")

    assertEquals("head\n    \ntail", document.render(80))
    assertEquals("head\n\ntail", document.renderTrim(80))
    assertEquals(document.render(80), document.renderStream(80).mkString)
    assertEquals(document.renderTrim(80), document.renderStreamTrim(80).mkString)

    val writer: StringWriter = new StringWriter()
    val printWriter: PrintWriter = new PrintWriter(writer)
    document.writeToTrim(80, printWriter)
    printWriter.flush()
    assertEquals("head\n\ntail", writer.toString)
  }

  @Test
  def stylesAndZeroWidthControlsDoNotAffectLayoutWidth(): Unit = {
    val warningStyle: Style = Style.Ansi.Fg.Red ++ Style.Ansi.Attr.Bold
    val backgroundStyle: Style = Style.XTerm.Bg.color(1, 2, 3)
    val styled: Doc =
      Doc.text("warn").style(warningStyle) + Doc.space + Doc.text("ok").style(backgroundStyle)

    assertEquals("warn ok", styled.unzero.render(4))
    assertEquals(
      "\u001b[31;1mwarn\u001b[0m \u001b[48;5;67mok\u001b[0m",
      styled.render(4)
    )

    val controlled: Doc = Doc.text("a") + Doc.ansiControl(31, 1) + Doc.zeroWidth("!") + Doc.text("b")
    assertEquals("a\u001b[31;1m!b", controlled.render(1))
    assertEquals("ab", controlled.unzero.render(1))
  }

  @Test
  def validatesXTermColorBoundsAndSupportsLaxColorHelpers(): Unit = {
    assertEquals("\u001b[38;5;16m", Style.XTerm.Fg.color(0, 0, 0).start)
    assertEquals("\u001b[38;5;231m", Style.XTerm.Fg.color(5, 5, 5).start)
    assertEquals("\u001b[48;5;232m", Style.XTerm.Bg.gray(0).start)
    assertEquals("\u001b[48;5;255m", Style.XTerm.Bg.gray(23).start)
    assertEquals("\u001b[38;5;231m", Style.XTerm.Fg.laxColor(9, 9, 9).start)
    assertEquals("\u001b[48;5;0m", Style.XTerm.Bg.laxColorCode(-10).start)

    assertThrows(classOf[IllegalArgumentException], new Executable {
      override def execute(): Unit = Style.XTerm.Fg.color(6, 0, 0)
    })
    assertThrows(classOf[IllegalArgumentException], new Executable {
      override def execute(): Unit = Style.XTerm.Bg.gray(24)
    })
    assertThrows(classOf[IllegalArgumentException], new Executable {
      override def execute(): Unit = Style.XTerm.Fg.colorCode(256)
    })
  }

  @Test
  def documentTypeClassInstancesRenderPrimitiveIterableAndCustomValues(): Unit = {
    import Document.ops._

    final case class Person(name: String, age: Int)

    implicit val personDocument: Document[Person] =
      Document[String].contramap[Person](person => s"${person.name}:${person.age}")

    assertEquals("hello", "hello".doc.render(80))
    assertEquals("true", true.doc.render(80))
    assertEquals("123", 123.doc.render(80))
    assertEquals("Ada:37", Person("Ada", 37).doc.render(80))

    val iterableDocument: Document[Iterable[Int]] = Document.documentIterable[Int]("List")
    assertEquals("List(1, 2, 3)", iterableDocument.document(List(1, 2, 3)).render(80))
    assertEquals("List(\n  1,\n  2,\n  3\n)", iterableDocument.document(List(1, 2, 3)).render(0))
  }

  @Test
  def deferredDocsAndDocumentsAreEvaluatedLazilyAndCached(): Unit = {
    var docEvaluations: Int = 0
    val deferredDoc: Doc = Doc.defer {
      docEvaluations += 1
      Doc.text("lazy")
    }

    assertEquals(0, docEvaluations)
    assertEquals("lazy", deferredDoc.render(80))
    assertEquals(1, docEvaluations)
    assertEquals("lazy", deferredDoc.render(80))
    assertEquals(1, docEvaluations)

    var documentEvaluations: Int = 0
    val deferredDocument: Document[String] = Document.defer {
      documentEvaluations += 1
      Document.instance[String](value => Doc.text(value.reverse))
    }

    assertEquals(0, documentEvaluations)
    assertEquals("cba", deferredDocument.document("abc").render(80))
    assertEquals(1, documentEvaluations)
    assertEquals("fed", deferredDocument.document("def").render(80))
    assertEquals(1, documentEvaluations)
  }

  @Test
  def exposesRepresentationFlatteningOrderingEquivalenceAndWidthMetadata(): Unit = {
    val composite: Doc = Doc.text("a") + (Doc.text("b") / Doc.text("c"))
    val flattened: Doc = composite.flatten

    assertEquals("ab\nc", composite.render(80))
    assertEquals("ab c", flattened.render(80))
    assertTrue(composite.flattenOption.nonEmpty)
    assertTrue(Doc.text("abc").flattenOption.isEmpty)
    assertEquals(
      "Concat(Text(a), Concat(Text(b), Concat(FlatAlt(Line, Text( )), Text(c))))",
      composite.representation().render(200)
    )
    assertEquals(2, composite.maxWidth)

    val sorted: List[String] = List(Doc.text("b"), Doc.text("a")).sorted(Doc.orderingAtWidth(10)).map(_.render(10))
    assertEquals(List("a", "b"), sorted)
    assertTrue(Doc.equivAtWidths(List(0, 10)).equiv(Doc.text("ab"), Doc.text("a") + Doc.text("b")))
    assertEquals(Doc.text("ab").hashCode, (Doc.text("a") + Doc.text("b")).hashCode)
  }
}

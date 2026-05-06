/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang_modules.scala_xml_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.io.StringReader
import java.io.StringWriter
import scala.jdk.CollectionConverters._
import scala.xml.Comment
import scala.xml.Elem
import scala.xml.MinimizeMode
import scala.xml.NamespaceBinding
import scala.xml.Node
import scala.xml.NodeBuffer
import scala.xml.Null
import scala.xml.PCData
import scala.xml.PrefixedAttribute
import scala.xml.PrettyPrinter
import scala.xml.ProcInstr
import scala.xml.Text
import scala.xml.TopScope
import scala.xml.UnprefixedAttribute
import scala.xml.Utility
import scala.xml.XML
import scala.xml.dtd.DocType
import scala.xml.dtd.PublicID
import scala.xml.dtd.SystemID
import scala.xml.transform.RewriteRule
import scala.xml.transform.RuleTransformer

final class RenameTitleElementRule extends RewriteRule {
  override def transform(node: Node): scala.collection.Seq[Node] = {
    node match {
      case element: Elem if element.label == "title" => scala.collection.immutable.Seq(element.copy(label = "name"))
      case other => scala.collection.immutable.Seq(other)
    }
  }
}

class Scala_xml_3Test {
  @Test
  def parsesNavigatesAndFiltersXmlDocuments(): Unit = {
    val document: Elem = XML
      .loadString(
        """
          |<catalog>
          |  <book id="b-1" category="fiction">
          |    <title>Neverwhere</title>
          |    <author>Neil Gaiman</author>
          |    <tags><tag>urban</tag><tag>fantasy</tag></tags>
          |  </book>
          |  <book id="b-2" category="non-fiction">
          |    <title>The Design of Everyday Things</title>
          |    <author>Don Norman</author>
          |  </book>
          |  <magazine id="m-1"><title>Scala Times</title></magazine>
          |</catalog>
          |""".stripMargin
      )
      .asInstanceOf[Elem]

    val directBooks: scala.xml.NodeSeq = document \ "book"
    val allTitles: List[String] = (document \\ "title").map(_.text.trim).toList
    val fictionBook: Node = directBooks.find(node => (node \@ "category") == "fiction").get

    assertThat(document.label).isEqualTo("catalog")
    assertThat(directBooks.map(_ \@ "id").toList.asJava).containsExactly("b-1", "b-2")
    assertThat(allTitles.asJava).containsExactly("Neverwhere", "The Design of Everyday Things", "Scala Times")
    assertThat((fictionBook \ "tags" \\ "tag").map(_.text).toList.asJava).containsExactly("urban", "fantasy")
    assertThat(fictionBook.attribute("category").map(_.text)).isEqualTo(Some("fiction"))
    assertThat((document \\ "missing").isEmpty).isTrue
  }

  @Test
  def preservesNamespacesAndNamespacedAttributes(): Unit = {
    val root: Elem = XML
      .loadString(
        """
          |<root xmlns:h="urn:html" xmlns:f="urn:furniture" h:id="root-1" plain="value">
          |  <h:table><h:tr><h:td>cell</h:td></h:tr></h:table>
          |  <f:table><f:name>African Coffee Table</f:name><f:width unit="cm">80</f:width></f:table>
          |</root>
          |""".stripMargin
      )
      .asInstanceOf[Elem]

    val htmlTable: Node = (root \ "table").find(_.prefix == "h").get
    val furnitureTable: Node = (root \ "table").find(_.prefix == "f").get
    val htmlCell: Node = (root \\ "td").head
    val furnitureWidth: Node = (furnitureTable \ "width").head

    assertThat(root.getNamespace("h")).isEqualTo("urn:html")
    assertThat(root.getNamespace("f")).isEqualTo("urn:furniture")
    assertThat(root.attribute("plain").map(_.text)).isEqualTo(Some("value"))
    assertThat(root.attribute("urn:html", "id").map(_.text)).isEqualTo(Some("root-1"))
    assertThat(htmlTable.namespace).isEqualTo("urn:html")
    assertThat(htmlCell.namespace).isEqualTo("urn:html")
    assertThat(furnitureTable.namespace).isEqualTo("urn:furniture")
    assertThat(furnitureWidth.attribute("unit").map(_.text)).isEqualTo(Some("cm"))
  }

  @Test
  def constructsCopiesAndUpdatesElementsWithAttributes(): Unit = {
    val scope: NamespaceBinding = NamespaceBinding("sku", "urn:sku", TopScope)
    val attributes = new PrefixedAttribute(
      "sku",
      "id",
      "A-1",
      new UnprefixedAttribute("kind", "keyboard", Null)
    )
    val item: Elem = Elem("sku", "item", attributes, scope, false, Text("standard keyboard"))
    val stockedItem: Elem = (item % new UnprefixedAttribute("stock", "5", Null))
      .copy(child = scala.collection.immutable.Seq(Text("mechanical keyboard")))
    val inventory: Elem = Elem(null, "inventory", Null, TopScope, false, stockedItem)

    assertThat(stockedItem.prefix).isEqualTo("sku")
    assertThat(stockedItem.namespace).isEqualTo("urn:sku")
    assertThat(stockedItem.attribute("urn:sku", "id").map(_.text)).isEqualTo(Some("A-1"))
    assertThat(stockedItem.attribute("kind").map(_.text)).isEqualTo(Some("keyboard"))
    assertThat(stockedItem.attribute("stock").map(_.text)).isEqualTo(Some("5"))
    assertThat(stockedItem.text).isEqualTo("mechanical keyboard")
    assertThat((inventory \ "item").head.text).isEqualTo("mechanical keyboard")
    assertThat(inventory.toString()).contains("xmlns:sku=\"urn:sku\"")
  }

  @Test
  def trimsPrettyPrintsEscapesAndWritesXml(): Unit = {
    val raw: Elem = XML
      .loadString(
        """
          |<message text="5 &lt; 7 &amp; &quot;quoted&quot;">
          |  Hello &lt;xml&gt; &amp; Scala
          |  <empty></empty>
          |</message>
          |""".stripMargin
      )
      .asInstanceOf[Elem]
    val trimmed: Node = Utility.trim(raw)
    val pretty: String = new PrettyPrinter(width = 80, step = 2).format(trimmed)
    val writer = new StringWriter()

    XML.write(writer, trimmed, "UTF-8", true, null, MinimizeMode.Never)

    assertThat(Utility.escape("5 < 7 & Scala")).isEqualTo("5 &lt; 7 &amp; Scala")
    assertThat(trimmed.text.trim).isEqualTo("Hello <xml> & Scala")
    assertThat(pretty).contains("Hello &lt;xml&gt; &amp; Scala")
    assertThat(pretty).contains("text=\"5 &lt; 7 &amp; &quot;quoted&quot;\"")
    assertThat(writer.toString).startsWith("<?xml version='1.0' encoding='UTF-8'?>")
    assertThat(writer.toString).contains("<empty></empty>")
  }

  @Test
  def serializesProcessingInstructionsCommentsCDataAndDoctypes(): Unit = {
    val buffer = new NodeBuffer()
    buffer &+ new ProcInstr("xml-stylesheet", "type=\"text/css\" href=\"style.css\"")
    buffer &+ new Comment("generated by scala-xml")
    buffer &+ new PCData("raw <markup> & data")

    val article: Elem = Elem(null, "article", Null, TopScope, false, buffer.toSeq*)
    val doctype = DocType("article", SystemID("article.dtd"), scala.collection.immutable.Seq.empty)
    val writer = new StringWriter()

    XML.write(writer, article, "UTF-8", false, doctype, MinimizeMode.Default)

    val serialized: String = writer.toString
    assertThat(article.child.map(_.label).toList.asJava).containsExactly("#PI", "#REM", "#PCDATA")
    assertThat(serialized).startsWith("<!DOCTYPE article SYSTEM \"article.dtd\">")
    assertThat(serialized).contains("<?xml-stylesheet type=\"text/css\" href=\"style.css\"?>")
    assertThat(serialized).contains("<!--generated by scala-xml-->")
    assertThat(serialized).contains("<![CDATA[raw <markup> & data]]>")
  }

  @Test
  def formatsPublicDoctypesAndLoadsFromReaders(): Unit = {
    val doctype = DocType(
      "html",
      PublicID("-//W3C//DTD XHTML 1.0 Strict//EN", "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"),
      scala.collection.immutable.Seq.empty
    )
    val writer = new StringWriter()
    val page: Elem = XML.load(new StringReader("<html><body><p>Hello</p></body></html>")).asInstanceOf[Elem]

    XML.write(writer, page, "UTF-8", true, doctype, MinimizeMode.Default)

    assertThat(page.label).isEqualTo("html")
    assertThat((page \\ "p").text).isEqualTo("Hello")
    assertThat(doctype.toString).contains("PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\"")
    assertThat(writer.toString).contains("<!DOCTYPE html PUBLIC")
    assertThat(writer.toString).contains("<p>Hello</p>")
  }

  @Test
  def transformsTreesWithRewriteRules(): Unit = {
    val catalog: Node = XML.loadString(
      """
        |<catalog>
        |  <book><title>Neverwhere</title><author>Neil Gaiman</author></book>
        |  <magazine><title>Scala Times</title></magazine>
        |</catalog>
        |""".stripMargin
    )
    val transformer = new RuleTransformer(new RenameTitleElementRule())
    val transformed: Elem = transformer.transform(catalog).head.asInstanceOf[Elem]

    assertThat((transformed \\ "title").isEmpty).isTrue
    assertThat((transformed \\ "name").map(_.text.trim).toList.asJava).containsExactly("Neverwhere", "Scala Times")
    assertThat((transformed \\ "author").text.trim).isEqualTo("Neil Gaiman")
  }
}

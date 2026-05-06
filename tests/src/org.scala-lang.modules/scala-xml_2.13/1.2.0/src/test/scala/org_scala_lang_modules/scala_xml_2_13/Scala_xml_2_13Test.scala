/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang_modules.scala_xml_2_13

import java.io.StringWriter

import scala.io.Source

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.xml.sax.SAXParseException

import scala.jdk.CollectionConverters._
import scala.xml.Elem
import scala.xml.Group
import scala.xml.Node
import scala.xml.NodeSeq
import scala.xml.PCData
import scala.xml.PrettyPrinter
import scala.xml.Text
import scala.xml.Unparsed
import scala.xml.Utility
import scala.xml.XML
import scala.xml.dtd.DocType
import scala.xml.dtd.SystemID
import scala.xml.pull.EvElemEnd
import scala.xml.pull.EvElemStart
import scala.xml.pull.EvText
import scala.xml.pull.XMLEventReader
import scala.xml.transform.RewriteRule
import scala.xml.transform.RuleTransformer

class Scala_xml_2_13Test {
  @Test
  def parsesElementsAttributesNamespacesAndCharacterData(): Unit = {
    val document: Elem = XML.loadString(
      """<?xml version="1.0" encoding="UTF-8"?>
        |<catalog xmlns:bk="urn:books" id="main">
        |  <bk:book bk:isbn="978-0134685991" available="true">
        |    <title>Effective Java</title>
        |    <description><![CDATA[Generics & lambdas <guide>]]></description>
        |  </bk:book>
        |  <bk:book bk:isbn="978-1617290657" available="false">
        |    <title>Functional Programming in Scala</title>
        |  </bk:book>
        |</catalog>
        |""".stripMargin
    )

    assertThat(document.label).isEqualTo("catalog")
    assertThat(document.attribute("id").map(_.text)).isEqualTo(Some("main"))
    assertThat(document.scope.getURI("bk")).isEqualTo("urn:books")

    val books: NodeSeq = document \\ "book"
    assertThat(books.length).isEqualTo(2)
    assertThat(books.head.prefix).isEqualTo("bk")
    assertThat(books.head.namespace).isEqualTo("urn:books")
    assertThat(books.head.attribute("urn:books", "isbn").map(_.text)).isEqualTo(Some("978-0134685991"))
    assertThat((books.head \ "description").text).isEqualTo("Generics & lambdas <guide>")
  }

  @Test
  def navigatesNodeSequencesAndFiltersByAttributes(): Unit = {
    val library: Elem =
      <library>
        <section name="programming">
          <book id="b1" available="true"><author>Ada</author><author>Grace</author></book>
          <book id="b2" available="false"><author>Alan</author></book>
        </section>
        <section name="math">
          <book id="b3" available="true"><author>Emmy</author></book>
        </section>
      </library>

    val sectionNames: Seq[String] = (library \ "section").map(node => (node \ "@name").text)
    assertThat(sectionNames.asJava).containsExactly("programming", "math")

    val availableBookIds: Seq[String] = (library \\ "book")
      .filter(book => (book \ "@available").text == "true")
      .map(book => (book \ "@id").text)
    assertThat(availableBookIds.asJava).containsExactly("b1", "b3")

    val authors: Seq[String] = (library \\ "book").head \ "author" map (_.text)
    assertThat(authors.asJava).containsExactly("Ada", "Grace")
    assertThat((library \ "missing").isEmpty).isTrue
  }

  @Test
  def constructsMixedContentAndRendersSpecialNodeTypes(): Unit = {
    val groupedLinks: Group = Group(Seq(<a href="/one">one</a>, <a href="/two">two</a>))
    val fragment: Elem =
      <article>
        <title>{Text("Escaped <title> & data")}</title>
        <body>{PCData("<p>raw but protected & readable</p>")}{Unparsed("<br/>")}{groupedLinks}</body>
      </article>

    val rendered: String = fragment.toString()
    assertThat(rendered).contains("Escaped &lt;title&gt; &amp; data")
    assertThat(rendered).contains("<![CDATA[<p>raw but protected & readable</p>]]>")
    assertThat(rendered).contains("<br/>")
    assertThat(groupedLinks.nodes.map(node => (node \ "@href").text).asJava).containsExactly("/one", "/two")
    assertThat(rendered).contains("raw but protected")
  }

  @Test
  def copiesElementsWhilePreservingAttributesAndChildren(): Unit = {
    val original: Elem = <book id="b1"><title>Old title</title><tags><tag>scala</tag></tags></book>
    val updated: Elem = original.copy(
      label = "updatedBook",
      child = Seq(<title>New title</title>) ++ (original \ "tags") ++ Seq(<published year="2024"/>)
    )

    assertThat(updated.label).isEqualTo("updatedBook")
    assertThat((updated \ "@id").text).isEqualTo("b1")
    assertThat((updated \ "title").text).isEqualTo("New title")
    assertThat((updated \\ "tag").text).isEqualTo("scala")
    assertThat(((updated \ "published").head \ "@year").text).isEqualTo("2024")
  }

  @Test
  def rewritesTreesWithRuleTransformer(): Unit = {
    val catalog: Elem =
      <catalog>
        <book id="b1"><title>Old title</title><obsolete>true</obsolete></book>
        <book id="b2"><title>Keep title</title><obsolete>false</obsolete></book>
      </catalog>

    val transformer: RuleTransformer = new RuleTransformer(new RewriteRule {
      override def transform(node: Node): Seq[Node] = node match {
        case elem: Elem if elem.label == "obsolete" => NodeSeq.Empty
        case elem: Elem if elem.label == "title" && elem.text == "Old title" => elem.copy(child = Seq(Text("New title")))
        case other => other
      }
    })

    val transformed: Elem = transformer.transform(catalog).head.asInstanceOf[Elem]

    assertThat((transformed \\ "obsolete").isEmpty).isTrue
    assertThat((transformed \ "book").head.attribute("id").map(_.text)).isEqualTo(Some("b1"))
    assertThat((transformed \\ "title").map(_.text).asJava).containsExactly("New title", "Keep title")
  }

  @Test
  def trimsWhitespaceEscapesTextAndPrettyPrintsRoundTrippableXml(): Unit = {
    val messy: Elem =
      <root>
        <item>
          value
        </item>
        <empty></empty>
      </root>

    val trimmed: Node = Utility.trim(messy)
    assertThat((trimmed \ "item").text).isEqualTo("value")
    val escaped: String = Utility.escape("5 < 7 & 9 > 3")
    assertThat(escaped).contains("&lt;").contains("&amp;").contains("&gt;")

    val pretty: String = new PrettyPrinter(width = 80, step = 2).format(trimmed)
    assertThat(pretty).contains("\n  <item>value</item>")
    assertThat(pretty).contains("<empty")

    val reparsed: Elem = XML.loadString(pretty)
    assertThat((reparsed \ "item").text).isEqualTo("value")
    assertThat((reparsed \ "empty").nonEmpty).isTrue
  }

  @Test
  def writesCompleteXmlDocumentWithDeclarationAndDoctype(): Unit = {
    val document: Elem =
      <report>
        <entry status="ok">completed</entry>
      </report>
    val doctype: DocType = DocType("report", SystemID("report.dtd"), Nil)
    val writer: StringWriter = new StringWriter()

    XML.write(writer, document, "UTF-8", true, doctype)

    val written: String = writer.toString
    assertThat(written).startsWith("<?xml version=")
    assertThat(written).contains("UTF-8")
    assertThat(written).contains("<!DOCTYPE report SYSTEM \"report.dtd\">")
    assertThat(written).contains("<entry status=\"ok\">completed</entry>")
  }

  @Test
  def streamsXmlAsPullEvents(): Unit = {
    val source: Source = Source.fromString(
      """<orders><order id="o1"><item quantity="2">pencils</item></order><order id="o2"/></orders>"""
    )

    try {
      val reader: XMLEventReader = new XMLEventReader(source)
      val events: List[scala.xml.pull.XMLEvent] = reader.toList

      val startLabels: Seq[String] = events.collect { case EvElemStart(_, label, _, _) => label }
      assertThat(startLabels.asJava).containsExactly("orders", "order", "item", "order")

      val orderIds: Seq[String] = events.collect { case EvElemStart(_, "order", attributes, _) => attributes.asAttrMap("id") }
      assertThat(orderIds.asJava).containsExactly("o1", "o2")

      val itemQuantities: Seq[String] = events.collect {
        case EvElemStart(_, "item", attributes, _) => attributes.asAttrMap("quantity")
      }
      assertThat(itemQuantities.asJava).containsExactly("2")
      assertThat(events.collect { case EvText(text) => text }.asJava).containsExactly("pencils")

      val endLabels: Seq[String] = events.collect { case EvElemEnd(_, label) => label }
      assertThat(endLabels.asJava).containsExactly("item", "order", "order", "orders")
    } finally {
      source.close()
    }
  }

  @Test
  def reportsMalformedXmlWithParserException(): Unit = {
    val error: SAXParseException = assertThrows(
      classOf[SAXParseException],
      () => {
        XML.loadString("<root><unclosed></root>")
        ()
      }
    )

    assertThat(error.getMessage).contains("unclosed")
  }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_xml;

import groovy.namespace.QName;
import groovy.util.Node;
import groovy.util.NodeList;
import groovy.xml.DOMBuilder;
import groovy.xml.Namespace;
import groovy.xml.XmlNodePrinter;
import groovy.xml.XmlParser;
import groovy.xml.XmlSlurper;
import groovy.xml.XmlUtil;
import groovy.xml.slurpersupport.GPathResult;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class Groovy_xmlTest {
    @Test
    void xmlParserBuildsMutableNodeTreeWithNamespacesAndAttributes() throws Exception {
        Namespace bookNamespace = new Namespace("urn:books", "bk");
        QName bookName = bookNamespace.get("book");
        QName titleName = bookNamespace.get("title");
        QName authorName = bookNamespace.get("author");
        XmlParser parser = new XmlParser(false, true);

        Node catalog = parser.parseText("""
                <bk:catalog xmlns:bk="urn:books">
                  <bk:book id="b1" available="true">
                    <bk:title>Groovy in Action</bk:title>
                    <bk:author>Paul King</bk:author>
                  </bk:book>
                </bk:catalog>
                """);

        assertThat(catalog.name()).isEqualTo(bookNamespace.get("catalog"));
        NodeList books = (NodeList) catalog.getAt(bookName);
        assertThat(books).hasSize(1);

        Node firstBook = (Node) books.get(0);
        assertThat(firstBook.attributes()).containsEntry("id", "b1");
        assertThat(firstBook.attributes()).containsEntry("available", "true");
        assertThat(((Node) ((NodeList) firstBook.getAt(titleName)).get(0)).text()).isEqualTo("Groovy in Action");

        Node secondBook = catalog.appendNode(bookName, Map.of("id", "b2", "available", "false"));
        secondBook.appendNode(titleName, "Native Groovy XML");
        secondBook.appendNode(authorName, "GraalVM");

        String serialized = XmlUtil.serialize(catalog);
        assertThat(serialized).contains("catalog");
        assertThat(serialized).contains("urn:books");
        assertThat(serialized).contains("b2");
        assertThat(serialized).contains("Native Groovy XML");
    }

    @Test
    void xmlSlurperNavigatesGPathResultsWithAttributesAndIndexes() throws Exception {
        XmlSlurper slurper = new XmlSlurper(false, false);
        GPathResult feed = slurper.parseText("""
                <feed>
                  <entry id="e1" category="news">
                    <title>First</title>
                    <author active="true">Ada</author>
                  </entry>
                  <entry id="e2" category="docs">
                    <title>Second</title>
                    <author active="false">Grace</author>
                  </entry>
                </feed>
                """);

        GPathResult entries = (GPathResult) feed.getProperty("entry");
        GPathResult firstEntry = (GPathResult) entries.getAt(0);
        GPathResult secondEntry = (GPathResult) entries.getAt(1);

        assertThat(entries.size()).isEqualTo(2);
        assertThat(firstEntry.getProperty("@id").toString()).isEqualTo("e1");
        assertThat(secondEntry.getProperty("@category").toString()).isEqualTo("docs");
        assertThat(((GPathResult) secondEntry.getProperty("title")).text()).isEqualTo("Second");
        assertThat(((GPathResult) firstEntry.getProperty("author")).text()).isEqualTo("Ada");
        GPathResult firstAuthor = (GPathResult) firstEntry.getProperty("author");
        assertThat(firstAuthor.getProperty("@active").toString()).isEqualTo("true");
    }

    @Test
    void xmlUtilEscapesXmlTextAndControlCharacters() {
        String xmlText = "Tom & Jerry <Cartoon> \"Best\" 'Classic'";
        String escapedText = XmlUtil.escapeXml(xmlText);

        assertThat(escapedText).isEqualTo("Tom &amp; Jerry &lt;Cartoon&gt; &quot;Best&quot; &apos;Classic&apos;");

        String escapedControls = XmlUtil.escapeControlCharacters("line\n tab\t zero" + (char) 0);
        assertThat(escapedControls).isEqualTo("line&#10; tab&#9; zero&#0;");
    }

    @Test
    void domBuilderCreatesStandardDomDocuments() throws Exception {
        String xml = """
                <inventory>
                  <item id="i1"><name>Parser</name><count>2</count></item>
                  <item id="i2"><name>Builder</name><count>5</count></item>
                </inventory>
                """;

        Document document = DOMBuilder.parse(new StringReader(xml));

        assertThat(document.getDocumentElement().getTagName()).isEqualTo("inventory");
        assertThat(document.getElementsByTagName("item").getLength()).isEqualTo(2);
        assertThat(document.getElementsByTagName("item").item(0).getAttributes().getNamedItem("id").getNodeValue()).isEqualTo("i1");
        assertThat(document.getElementsByTagName("name").item(1).getTextContent()).isEqualTo("Builder");
    }

    @Test
    void xmlNodePrinterRendersParsedNodesWithConfiguredWhitespace() throws Exception {
        Node root = new XmlParser(false, false).parseText("<root><entry id=\"1\">alpha</entry><entry id=\"2\">beta</entry></root>");
        StringWriter writer = new StringWriter();
        XmlNodePrinter printer = new XmlNodePrinter(new PrintWriter(writer));
        printer.setPreserveWhitespace(true);
        printer.setExpandEmptyElements(true);

        printer.print(root);

        GPathResult parsed = new XmlSlurper(false, false).parseText(writer.toString());
        GPathResult entries = (GPathResult) parsed.getProperty("entry");
        assertThat(((GPathResult) entries.getAt(0)).getProperty("@id").toString()).isEqualTo("1");
        assertThat(((GPathResult) entries.getAt(1)).getProperty("@id").toString()).isEqualTo("2");
        assertThat(((GPathResult) entries.getAt(0)).text()).isEqualTo("alpha");
        assertThat(((GPathResult) entries.getAt(1)).text()).isEqualTo("beta");
    }

    @Test
    void namespaceCreatesQualifiedNamesUsableWithParsedNodes() throws Exception {
        Namespace namespace = new Namespace("urn:catalog", "cat");
        QName entryName = namespace.get("entry");
        QName titleName = namespace.get("title");

        assertThat(titleName.getLocalPart()).isEqualTo("title");
        assertThat(titleName.getNamespaceURI()).isEqualTo("urn:catalog");
        assertThat(titleName.getQualifiedName()).isEqualTo("cat:title");

        Node root = new XmlParser(false, true).parseText("""
                <records xmlns:cat="urn:catalog">
                  <cat:entry id="e1"><cat:title>Namespaced title</cat:title></cat:entry>
                </records>
                """);

        Node entry = (Node) ((NodeList) root.getAt(entryName)).get(0);
        assertThat(((Node) ((NodeList) entry.getAt(titleName)).get(0)).text()).isEqualTo("Namespaced title");
    }
}

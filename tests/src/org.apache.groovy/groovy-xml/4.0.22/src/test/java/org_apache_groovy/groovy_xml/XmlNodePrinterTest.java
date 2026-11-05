/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_xml;

import groovy.util.IndentPrinter;
import groovy.util.Node;
import groovy.xml.XmlNodePrinter;
import groovy.xml.XmlParser;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlNodePrinterTest {
    @Test
    void printsParsedNodesWithIndentation() throws Exception {
        Node catalog = new XmlParser().parseText("""
                <catalog region="EU"><book id="b1"><title>Groovy &amp; XML</title><cover/></book></catalog>
                """);
        StringWriter writer = new StringWriter();

        new XmlNodePrinter(new PrintWriter(writer), "  ").print(catalog);

        assertThat(writer.toString()).isEqualToNormalizingNewlines("""
                <catalog region="EU">
                  <book id="b1">
                    <title>
                      Groovy &amp; XML
                    </title>
                    <cover/>
                  </book>
                </catalog>
                """);
    }

    @Test
    void appliesQuotePreserveWhitespaceAndEmptyElementOptions() throws Exception {
        Node note = new Node(null, "note", Map.of("lang", "en"));
        note.appendNode("body", "Keep <this> text");
        note.appendNode("signature");
        StringWriter writer = new StringWriter();
        XmlNodePrinter printer = new XmlNodePrinter(new IndentPrinter(new PrintWriter(writer), ""), "'");
        printer.setPreserveWhitespace(true);
        printer.setExpandEmptyElements(true);

        printer.print(note);

        assertThat(printer.getQuote()).isEqualTo("'");
        assertThat(printer.isPreserveWhitespace()).isTrue();
        assertThat(printer.isExpandEmptyElements()).isTrue();
        assertThat(writer.toString()).isEqualToNormalizingNewlines("""
                <note lang='en'>
                <body>Keep &lt;this&gt; text</body>
                <signature></signature>
                </note>
                """);
    }

    @Test
    void printsNamespaceDeclarationsForQualifiedNames() throws Exception {
        Node catalog = new XmlParser(false, true).parseText("""
                <bk:catalog xmlns:bk="urn:books"><bk:book>Groovy</bk:book></bk:catalog>
                """);
        StringWriter namespaced = new StringWriter();
        StringWriter plain = new StringWriter();
        XmlNodePrinter plainPrinter = new XmlNodePrinter(new PrintWriter(plain), "");
        plainPrinter.setNamespaceAware(false);

        new XmlNodePrinter(new PrintWriter(namespaced), "").print(catalog);
        plainPrinter.print(catalog);

        assertThat(plainPrinter.isNamespaceAware()).isFalse();
        assertThat(namespaced.toString()).contains("<bk:catalog xmlns:bk=\"urn:books\">").contains("<bk:book>");
        assertThat(plain.toString()).contains("<catalog>").contains("<book>").doesNotContain("xmlns");
    }
}

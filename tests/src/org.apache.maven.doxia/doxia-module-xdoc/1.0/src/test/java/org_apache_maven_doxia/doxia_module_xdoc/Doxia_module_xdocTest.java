/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_module_xdoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.io.StringWriter;

import org.apache.maven.doxia.module.xdoc.XmlWriterXdocSink;
import org.apache.maven.doxia.module.xdoc.XdocParser;
import org.apache.maven.doxia.module.xdoc.XdocSink;
import org.apache.maven.doxia.module.xdoc.XdocSiteModule;
import org.apache.maven.doxia.parser.Parser;
import org.apache.maven.doxia.sink.Sink;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.junit.jupiter.api.Test;

public class Doxia_module_xdocTest {
    @Test
    void siteModuleDescribesXdocSources() {
        XdocSiteModule module = new XdocSiteModule();

        assertThat(module.getSourceDirectory()).isEqualTo("xdoc");
        assertThat(module.getExtension()).isEqualTo("xml");
        assertThat(module.getParserId()).isEqualTo("xdoc");
    }

    @Test
    void xdocSinkWritesCompleteDocumentWithCommonInlineAndBlockElements() {
        StringWriter writer = new StringWriter();
        XdocSink sink = new XdocSink(writer);

        sink.head();
        sink.text("Sample <Document>");
        sink.title_();
        sink.text("Jane Developer");
        sink.author_();
        sink.text("2026-05-10");
        sink.date_();
        sink.head_();
        sink.body();
        sink.section1();
        sink.sectionTitle1();
        sink.text("Overview & Usage");
        sink.sectionTitle1_();
        sink.paragraph();
        sink.text("Use ");
        sink.bold();
        sink.text("bold");
        sink.bold_();
        sink.text(", ");
        sink.italic();
        sink.text("italic");
        sink.italic_();
        sink.text(", ");
        sink.monospaced();
        sink.text("code <tag>");
        sink.monospaced_();
        sink.text(" and a ");
        sink.link("https://example.invalid/docs?x=1&y=2");
        sink.text("link");
        sink.link_();
        sink.text(".");
        sink.paragraph_();
        sink.anchor("local anchor");
        sink.text("anchor text");
        sink.anchor_();
        sink.nonBreakingSpace();
        sink.lineBreak();
        sink.horizontalRule();
        sink.figure();
        sink.figureGraphics("images/logo.png");
        sink.figureCaption();
        sink.text("Logo & mark");
        sink.figureCaption_();
        sink.figure_();
        sink.section1_();
        sink.body_();
        sink.close();

        assertThat(writer.toString())
                .contains("<?xml version=\"1.0\" ?>")
                .contains("<document>")
                .contains("<properties>")
                .contains("<title>Sample &lt;Document&gt;</title>")
                .contains("<author>Jane Developer</author>")
                .contains("<date>2026-05-10</date>")
                .contains("<section name=\"Overview &amp; Usage\">")
                .contains("Use <b>bold</b>")
                .contains("<i>italic</i>")
                .contains("<tt>code &lt;tag&gt;</tt>")
                .contains("and a")
                .contains("<a href=\"https://example.invalid/docs?x=1&y=2\">link</a>")
                .contains("<a id=\"local_anchor\" name=\"local_anchor\">anchor text</a>")
                .contains("&#160;")
                .contains("<br />")
                .contains("<hr />")
                .contains("<img src=\"images/logo.png\" alt=\"Logo &amp; mark\" />")
                .contains("</document>");
    }

    @Test
    void xdocSinkWritesCollectionsTablesAndVerbatimBlocks() {
        StringWriter writer = new StringWriter();
        XdocSink sink = new XdocSink(writer);

        sink.body();
        sink.list();
        sink.listItem();
        sink.text("first bullet");
        sink.listItem_();
        sink.list_();
        sink.numberedList(Sink.NUMBERING_UPPER_ROMAN);
        sink.numberedListItem();
        sink.text("roman item");
        sink.numberedListItem_();
        sink.numberedList_();
        sink.definitionList();
        sink.definedTerm();
        sink.text("term");
        sink.definedTerm_();
        sink.definition();
        sink.text("definition");
        sink.definition_();
        sink.definitionList_();
        sink.verbatim(true);
        sink.text("if (a < b) {\n  return a & b;\n}");
        sink.verbatim_();
        sink.tableRows(new int[] {Parser.JUSTIFY_LEFT, Parser.JUSTIFY_RIGHT}, true);
        sink.tableRow();
        sink.tableHeaderCell();
        sink.text("Name");
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text("Value");
        sink.tableHeaderCell_();
        sink.tableRow_();
        sink.tableRow();
        sink.tableCell();
        sink.text("alpha");
        sink.tableCell_();
        sink.tableCell();
        sink.text("42");
        sink.tableCell_();
        sink.tableRow_();
        sink.tableRows_();
        sink.body_();
        sink.close();

        assertThat(writer.toString())
                .contains("<ul>", "<li>first bullet</li>", "</ul>")
                .contains("<ol style=\"list-style-type: upper-roman\">", "<li>roman item</li>", "</ol>")
                .contains("<dl>", "<dt>term</dt>", "<dd>definition</dd>", "</dl>")
                .contains("<source>")
                .contains("if (a &lt; b) {\n  return a &amp; b;\n}")
                .contains("<table align=\"center\" border=\"1\">")
                .contains("<tr valign=\"top\">")
                .contains("<th>Name</th>", "<th>Value</th>")
                .contains("<td>alpha</td>", "<td>42</td>");
    }

    @Test
    void xdocSinkWritesTableCaptionAsItalicParagraph() {
        StringWriter writer = new StringWriter();
        XdocSink sink = new XdocSink(writer);

        sink.body();
        sink.tableCaption();
        sink.text("Module summary <caption> & notes");
        sink.tableCaption_();
        sink.body_();
        sink.close();

        String normalizedXdoc = writer.toString().replaceAll("\\s+", " ");
        assertThat(normalizedXdoc)
                .contains("<p><i>Module summary &lt;caption&gt; &amp; notes</i> </p>");
    }

    @Test
    void parserConvertsXdocMarkupToSinkEvents() throws Exception {
        String xdoc = """
                <document>
                  <properties>
                    <title>Parser Test</title>
                    <author>Apache Maven</author>
                  </properties>
                  <body>
                    <section name="Getting Started">
                      <p>Before <b>bold</b> and <i>italic</i>.</p>
                      <subsection name="Links">
                        <p><a href="https://example.invalid/ref">reference</a> and <a name="local-id">target</a>.</p>
                        <source>line 1 &amp; line 2</source>
                        <ul><li>bullet</li></ul>
                        <ol><li>one</li></ol>
                        <dl><dt>term</dt><dd>definition</dd></dl>
                        <table><tr><th>Column</th><td>Cell</td></tr></table>
                        <img src="images/pic.png" alt="Picture" />
                      </subsection>
                    </section>
                  </body>
                </document>
                """;
        StringWriter writer = new StringWriter();

        new XdocParser().parse(new StringReader(xdoc), new XdocSink(writer));

        assertThat(writer.toString())
                .contains("<title>Parser Test</title>")
                .contains("<author>Apache Maven</author>")
                .contains("<section name=\"Getting Started\">")
                .contains("<p>Before <b>bold</b>")
                .contains("<i>italic</i>.")
                .contains("<subsection name=\"Links\">")
                .contains("<a href=\"https://example.invalid/ref\">reference</a>")
                .contains("<a id=\"local-id\" name=\"local-id\">target</a>")
                .contains("<source>", "line 1 &amp; line 2", "</source>")
                .contains("<ul>", "<ol style=\"list-style-type: decimal\">", "<dl>")
                .contains("<table align=\"center\">")
                .contains("<th>Column</th>", "<td>Cell</td>")
                .contains("<img src=\"images/pic.png\" alt=\"Picture\" />")
                .contains("</document>");
    }

    @Test
    void parserConvertsLowerLevelHeadings() throws Exception {
        String xdoc = """
                <document>
                  <body>
                    <section name="Reference">
                      <subsection name="Details">
                        <h4>Command Options</h4>
                        <h5>Environment Variables</h5>
                        <h6>Fine Print</h6>
                      </subsection>
                    </section>
                  </body>
                </document>
                """;
        StringWriter writer = new StringWriter();

        new XdocParser().parse(new StringReader(xdoc), new XdocSink(writer));

        String normalizedXdoc = writer.toString().replaceAll("\\s+", " ");
        assertThat(normalizedXdoc)
                .contains("<h4>Command Options</h4>")
                .contains("<h5>Environment Variables</h5>")
                .contains("<h6>Fine Print</h6>");
    }

    @Test
    void parserKeepsTextFromUnknownElements() throws Exception {
        String xdoc = """
                <document>
                  <body>
                    <custom data="value"><nested>raw text</nested></custom>
                    <custom-empty data="empty" />
                  </body>
                </document>
                """;
        StringWriter writer = new StringWriter();

        new XdocParser().parse(new StringReader(xdoc), new XdocSink(writer));

        assertThat(writer.toString())
                .contains("<body>raw text</body>")
                .doesNotContain("custom data")
                .doesNotContain("custom-empty");
    }

    @Test
    void parserRejectsMalformedMacroDeclarations() {
        String missingMacroName = """
                <document>
                  <body>
                    <macro><param name="key" value="value" /></macro>
                  </body>
                </document>
                """;
        String missingParamValue = """
                <document>
                  <body>
                    <macro name="snippet"><param name="key" /></macro>
                  </body>
                </document>
                """;

        assertThatThrownBy(() -> parseWithXdocSink(missingMacroName))
                .hasMessageContaining("name")
                .hasMessageContaining("macro");
        assertThatThrownBy(() -> parseWithXdocSink(missingParamValue))
                .hasMessageContaining("name")
                .hasMessageContaining("value")
                .hasMessageContaining("param");
    }

    private static void parseWithXdocSink(String xdoc) throws Exception {
        new XdocParser().parse(new StringReader(xdoc), new XdocSink(new StringWriter()));
    }

    @Test
    void xmlWriterSinkWritesStructuredXdocWithAttributes() {
        StringWriter writer = new StringWriter();
        XmlWriterXdocSink sink = new XmlWriterXdocSink(new PrettyPrintXMLWriter(writer));

        sink.head();
        sink.text("XML Writer Title");
        sink.title_();
        sink.head_();
        sink.body();
        sink.section1();
        sink.sectionTitle();
        sink.text("Writer Section");
        sink.sectionTitle_();
        sink.paragraph();
        sink.text("Plain paragraph");
        sink.paragraph_();
        sink.tableRows(new int[] {Parser.JUSTIFY_LEFT, Parser.JUSTIFY_RIGHT}, false);
        sink.tableRow();
        sink.tableCell();
        sink.text("left");
        sink.tableCell_();
        sink.tableCell();
        sink.text("right");
        sink.tableCell_();
        sink.tableRow_();
        sink.tableRows_();
        sink.verbatim(false);
        sink.text("two words");
        sink.verbatim_();
        sink.section1_();
        sink.body_();
        sink.close();

        assertThat(writer.toString())
                .contains("<document>")
                .contains("<properties>")
                .contains("<title>XML Writer Title</title>")
                .contains("<body>")
                .contains("<section name=\"Writer Section\">")
                .contains("<p>Plain paragraph</p>")
                .contains("<table align=\"center\" border=\"0\">")
                .contains("<td align=\"left\">left</td>")
                .contains("<td align=\"right\">right</td>")
                .contains("<pre>two&amp;nbsp;words</pre>")
                .contains("</document>");
    }

    @Test
    void staticHtmlHelpersHandleEscapingAndUrlEncoding() {
        assertThat(XdocSink.escapeHTML("<tag attr=\"value\">Tom & Jerry</tag>"))
                .isEqualTo("&lt;tag attr=&quot;value&quot;&gt;Tom &amp; Jerry&lt;/tag&gt;");
        assertThat(XmlWriterXdocSink.escapeHTML(null)).isEmpty();
        assertThat(XdocSink.encodeURL("folder name/file+1.html#A&B"))
                .isEqualTo(XmlWriterXdocSink.encodeURL("folder name/file+1.html#A&B"))
                .contains("folder%20name");
    }
}

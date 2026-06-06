/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_module_xhtml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.apache.maven.doxia.module.xhtml.XhtmlParser;
import org.apache.maven.doxia.module.xhtml.XhtmlSink;
import org.apache.maven.doxia.module.xhtml.XhtmlSinkFactory;
import org.apache.maven.doxia.module.xhtml.XhtmlSiteModule;
import org.apache.maven.doxia.parser.ParseException;
import org.apache.maven.doxia.parser.Parser;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.SinkEventAttributeSet;
import org.apache.maven.doxia.sink.SinkEventAttributes;
import org.junit.jupiter.api.Test;

public class Doxia_module_xhtmlTest {
    @Test
    void sinkWritesDocumentHeadMetadataAndEscapedParagraphContent() {
        StringWriter writer = new StringWriter();
        XhtmlSink sink = new TestXhtmlSink(writer, "UTF-8", "en");

        sink.head();
        sink.title();
        sink.text("User Guide");
        sink.title_();
        sink.text("Jane Author");
        sink.author_();
        sink.text("2026-05-09");
        sink.date_();
        sink.head_();
        sink.body();
        sink.section1();
        sink.sectionTitle1();
        sink.text("Overview");
        sink.sectionTitle1_();
        sink.paragraph();
        sink.text("5 < 6 & \"quoted\"");
        sink.rawText("<span class=\"raw\">raw html</span>");
        sink.paragraph_();
        sink.section1_();
        sink.body_();
        sink.close();

        String html = writer.toString();
        assertThat(html)
                .contains("<!DOCTYPE html PUBLIC")
                .contains("<html xmlns=\"http://www.w3.org/1999/xhtml\"")
                .contains("lang=\"en\"")
                .contains("xml:lang=\"en\"")
                .contains("<head>")
                .contains("<title>User Guide</title>")
                .contains("<meta name=\"author\" content=\"Jane Author\" />")
                .contains("<meta name=\"date\" content=\"2026-05-09\" />")
                .contains("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>")
                .contains("<body>")
                .contains("<div class=\"section\">")
                .contains("<h2>Overview</h2>")
                .contains("5 &lt; 6 &amp; &quot;quoted&quot;")
                .contains("<span class=\"raw\">raw html</span>")
                .contains("</body>")
                .contains("</html>");
    }

    @Test
    void sinkWritesLinksAnchorsInlineMarkupImagesListsTablesAndSpecialWhitespace() {
        StringWriter writer = new StringWriter();
        XhtmlSink sink = new TestXhtmlSink(writer);

        sink.body();
        sink.anchor("1 intro anchor");
        sink.anchor_();
        sink.paragraph();
        SinkEventAttributeSet linkAttributes = attribute(SinkEventAttributes.TARGET, "_blank");
        sink.link("https://example.com/search?q=one&lang=en", linkAttributes);
        sink.text("external");
        sink.link_();
        sink.text(" ");
        sink.link("local-section");
        sink.bold();
        sink.italic();
        sink.monospaced();
        sink.text("local link");
        sink.monospaced_();
        sink.italic_();
        sink.bold_();
        sink.link_();
        sink.lineBreak();
        sink.nonBreakingSpace();
        sink.paragraph_();
        sink.verbatim(SinkEventAttributeSet.BOXED);
        sink.text("<xml>&value</xml>");
        sink.verbatim_();
        sink.figure();
        sink.figureGraphics("images/logo.png");
        sink.figureCaption();
        sink.text("Logo");
        sink.figureCaption_();
        sink.figure_();
        sink.list();
        sink.listItem();
        sink.text("bullet");
        sink.listItem_();
        sink.list_();
        sink.numberedList(Sink.NUMBERING_UPPER_ALPHA);
        sink.numberedListItem();
        sink.text("numbered");
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
        sink.table();
        sink.tableCaption();
        sink.text("Metrics");
        sink.tableCaption_();
        sink.tableRows(new int[] {Sink.JUSTIFY_LEFT, Sink.JUSTIFY_CENTER, Sink.JUSTIFY_RIGHT}, true);
        sink.tableRow();
        sink.tableHeaderCell(attribute(SinkEventAttributes.WIDTH, "25%"));
        sink.text("Name");
        sink.tableHeaderCell_();
        sink.tableCell(attribute(SinkEventAttributes.WIDTH, "50%"));
        sink.text("Middle");
        sink.tableCell_();
        sink.tableCell();
        sink.text("Value");
        sink.tableCell_();
        sink.tableRow_();
        sink.tableRows_();
        sink.table_();
        sink.horizontalRule();
        sink.body_();
        sink.close();

        String html = writer.toString();
        assertThat(html)
                .contains("<a name=\"a1_intro_anchor\"></a>")
                .contains("target=\"_blank\"")
                .contains("class=\"externalLink\"")
                .contains("href=\"https://example.com/search?q=one&amp;lang=en\"")
                .contains("href=\"local-section\"")
                .contains("<b><i><tt>local link</tt></i></b>")
                .contains("<br />")
                .contains("&#160;")
                .contains("<div class=\"source\"><pre>&lt;xml&gt;&amp;value&lt;/xml&gt;</pre>")
                .contains("<img src=\"images/logo.png\" alt=\"Logo\" />")
                .contains("<ul><li>bullet</li>")
                .contains("<ol style=\"list-style-type: upper-alpha\"><li>numbered</li>")
                .contains("<dl><dt>term</dt>")
                .contains("<dd>definition</dd>")
                .contains("<table align=\"center\" border=\"1\" class=\"bodyTable\">")
                .contains("<caption>Metrics</caption>")
                .contains("<tr class=\"a\">")
                .contains("<th")
                .contains("width=\"25%\"")
                .contains("align=\"left\"")
                .contains(">Name</th>")
                .contains("<td")
                .contains("width=\"50%\"")
                .contains("align=\"center\"")
                .contains(">Middle</td>")
                .contains("align=\"right\"")
                .contains(">Value</td>")
                .contains("<hr />");
    }

    @Test
    void parserConvertsXhtmlDocumentIntoXhtmlSinkOutput() throws Exception {
        String xhtml = """
                <html>
                  <head><title>Parsed Title</title></head>
                  <body>
                    <h1>Main &amp; Intro</h1>
                    <p>Before <strong>bold</strong> and <em>italic</em> text.</p>
                    <h2>Details</h2>
                    <p><a href="https://example.com">site</a> and <a name="inside">anchor</a>.</p>
                    <pre>code &amp; symbols</pre>
                    <ul><li>first</li><li>second</li></ul>
                    <ol><li>one</li></ol>
                    <table><tr><th>head</th><td>cell</td></tr></table>
                    <img src="diagram.png" alt="Diagram" />
                    <hr />
                  </body>
                </html>
                """;
        StringWriter writer = new StringWriter();
        XhtmlParser parser = new XhtmlParser();
        TestXhtmlSink sink = new TestXhtmlSink(writer);

        parser.parse(new StringReader(xhtml), sink);
        sink.close();

        String html = writer.toString();
        assertThat(parser.getType()).isEqualTo(Parser.XML_TYPE);
        assertThat(html)
                .contains("<title>Parsed Title</title>")
                .contains("<h1>Main &amp; Intro</h1>")
                .contains("<p>Before <b>bold</b> and <i>italic</i> text.</p>")
                .contains("<h2>Details</h2>")
                .contains("class=\"externalLink\"")
                .contains("href=\"https://example.com\"")
                .contains(">site</a>")
                .contains("<a name=\"inside\">anchor</a>")
                .contains("<div><pre>code &amp; symbols</pre>")
                .contains("<ul><li>first</li>")
                .contains("<li>second</li>")
                .contains("<ol style=\"list-style-type: decimal\"><li>one</li>")
                .contains("<table align=\"center\" border=\"1\" class=\"bodyTable\"><tr class=\"a\">")
                .contains("<th align=\"left\">head</th>")
                .contains("<td align=\"left\">cell</td>")
                .contains("<img src=\"diagram.png\" alt=\"Diagram\" />")
                .contains("<hr />")
                .contains("</div>")
                .contains("</body>")
                .contains("</html>");
    }

    @Test
    void parserConvertsAddressElementIntoAuthorMetadata() throws Exception {
        String xhtml = """
                <html>
                  <head><title>Project Documentation</title><address>Documentation Team</address></head>
                  <body><p>content</p></body>
                </html>
                """;
        StringWriter writer = new StringWriter();
        XhtmlParser parser = new XhtmlParser();
        TestXhtmlSink sink = new TestXhtmlSink(writer);

        parser.parse(new StringReader(xhtml), sink);
        sink.close();

        String html = writer.toString();
        assertThat(html)
                .contains("<title>Project Documentation</title>")
                .contains("<meta name=\"author\" content=\"Documentation Team\" />")
                .contains("<p>content</p>");
    }

    @Test
    void parserPreservesTopLevelHeadingAndWrapsNestedSection() throws Exception {
        String xhtml = """
                <html>
                  <body>
                    <h1>First</h1>
                    <p>one</p>
                    <h2>Child</h2>
                    <p>two</p>
                    <h1>Second</h1>
                    <p>three</p>
                  </body>
                </html>
                """;
        StringWriter writer = new StringWriter();
        XhtmlParser parser = new XhtmlParser();
        TestXhtmlSink sink = new TestXhtmlSink(writer);

        parser.parse(new StringReader(xhtml), sink);
        sink.close();

        String html = writer.toString().replaceAll(">\\s+<", "><");
        assertThat(html).contains(
                "<body><h1>First</h1><p>one</p><div class=\"section\"><h2>Child</h2><p>two</p>"
                        + "<h1>Second</h1><p>three</p></div></body></html>");
    }

    @Test
    void parserReportsMalformedXmlAsParseException() {
        XhtmlParser parser = new XhtmlParser();

        assertThatThrownBy(() -> parser.parse(new StringReader("<html><body><p>unfinished</body></html>"),
                new TestXhtmlSink(new StringWriter())))
                .isInstanceOf(ParseException.class);
    }

    @Test
    void factorySiteModuleAndProtectedHelpersExposeXhtmlBehavior() throws Exception {
        XhtmlSiteModule siteModule = new XhtmlSiteModule();
        XhtmlSinkFactory factory = new XhtmlSinkFactory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        Sink sink = factory.createSink(output, "UTF-8");
        sink.head();
        sink.title();
        sink.text("Factory Sink");
        sink.title_();
        sink.head_();
        sink.body();
        sink.paragraph();
        sink.text("Created by the XHTML sink factory");
        sink.paragraph_();
        sink.body_();
        sink.close();

        String html = output.toString(StandardCharsets.UTF_8.name());
        assertThat(siteModule.getSourceDirectory()).isEqualTo("xhtml");
        assertThat(siteModule.getExtension()).isEqualTo("xhtml");
        assertThat(siteModule.getParserId()).isEqualTo("xhtml");
        assertThat(html)
                .contains("<title>Factory Sink</title>")
                .contains("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>")
                .contains("<p>Created by the XHTML sink factory</p>");
        assertThat(TestXhtmlSink.escapeHtml("<tag attr=\"value\">&</tag>"))
                .isEqualTo("&lt;tag attr=&quot;value&quot;&gt;&amp;&lt;/tag&gt;");
        assertThat(TestXhtmlSink.encodeUrl("folder/My File.xhtml?x=1&y=\u00e4"))
                .isEqualTo("folder/My%20File.xhtml?x=1&y=%c3%a4");
        assertThat(TestXhtmlSink.encodeUrl(null)).isNull();
    }

    private static SinkEventAttributeSet attribute(String name, String value) {
        SinkEventAttributeSet attributes = new SinkEventAttributeSet();
        attributes.addAttribute(name, value);
        return attributes;
    }

    private static final class TestXhtmlSink extends XhtmlSink {
        private TestXhtmlSink(Writer writer) {
            super(writer);
        }

        private TestXhtmlSink(Writer writer, String encoding, String languageId) {
            super(writer, encoding, languageId);
        }

        private static String escapeHtml(String value) {
            return escapeHTML(value);
        }

        private static String encodeUrl(String value) {
            return encodeURL(value);
        }
    }
}

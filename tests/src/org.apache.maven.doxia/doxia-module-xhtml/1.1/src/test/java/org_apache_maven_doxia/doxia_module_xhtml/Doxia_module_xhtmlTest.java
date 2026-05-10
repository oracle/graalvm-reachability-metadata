/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_module_xhtml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.doxia.module.xhtml.StringsMap;
import org.apache.maven.doxia.module.xhtml.XhtmlParser;
import org.apache.maven.doxia.module.xhtml.XhtmlSink;
import org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext;
import org.apache.maven.doxia.parser.ParseException;
import org.apache.maven.doxia.parser.Parser;
import org.apache.maven.doxia.sink.Sink;
import org.junit.jupiter.api.Test;

public class Doxia_module_xhtmlTest {
    @Test
    void sinkWritesDocumentHeadMetadataAndEscapedParagraphContent() {
        StringWriter writer = new StringWriter();
        RenderingContext renderingContext = new RenderingContext(new File("target/site"), "guide/index.xhtml");
        XhtmlSink sink = new XhtmlSink(writer, renderingContext);

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
        sink.flush();

        String html = writer.toString();
        assertThat(html)
                .contains("<!DOCTYPE html PUBLIC")
                .contains("<html xmlns=\"http://www.w3.org/1999/xhtml\">")
                .contains("<head>")
                .contains("<title>User Guide</title>")
                .contains("<meta name=\"author\" content=\"Jane Author\" />")
                .contains("<meta name=\"date\" content=\"2026-05-09\" />")
                .contains("<body>")
                .contains("<div class=\"section\">")
                .contains("<h2>Overview</h2>")
                .contains("5 &lt; 6 &amp; &quot;quoted&quot;")
                .contains("<span class=\"raw\">raw html</span>")
                .contains("</body>")
                .contains("</html>");
        assertThat(sink.getRenderingContext()).isSameAs(renderingContext);
    }

    @Test
    void sinkWritesLinksAnchorsInlineMarkupImagesListsTablesAndSpecialWhitespace() {
        StringWriter writer = new StringWriter();
        XhtmlSink sink = new XhtmlSink(writer);

        sink.body();
        sink.anchor("1 intro anchor");
        sink.anchor_();
        sink.paragraph();
        sink.link("https://example.com/search?q=one&lang=en", "_blank");
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
        sink.verbatim(true);
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
        sink.tableRows(new int[] {Parser.JUSTIFY_LEFT, Parser.JUSTIFY_CENTER, Parser.JUSTIFY_RIGHT}, true);
        sink.tableRow();
        sink.tableHeaderCell("25%");
        sink.text("Name");
        sink.tableHeaderCell_();
        sink.tableCell("50%");
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
        sink.flush();

        String html = writer.toString();
        assertThat(html)
                .contains("<a name=\"a1_intro_anchor\"></a>")
                .contains("target=\"_blank\"")
                .contains("class=\"externalLink\"")
                .contains("href=\"https://example.com/search?q=one&amp;lang=en\"")
                .contains("href=\"#local-section\"")
                .contains("<b><i><tt>local link</tt></i></b>")
                .contains("<br />")
                .contains("&#160;")
                .contains("<div class=\"source\"><pre>&lt;xml&gt;&amp;value&lt;/xml&gt;</pre>")
                .contains("<img src=\"images/logo.png\" alt=\"Logo\" />")
                .contains("<ul><li>bullet</li>")
                .contains("<ol type=\"A\"><li>numbered</li>")
                .contains("<dl><dt>term</dt>")
                .contains("<dd>definition</dd>")
                .contains("<table class=\"bodyTable\">")
                .contains("<caption>Metrics</caption>")
                .contains("<tbody>")
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

        parser.parse(new StringReader(xhtml), new XhtmlSink(writer));

        String html = writer.toString();
        assertThat(parser.getType()).isEqualTo(Parser.XML_TYPE);
        assertThat(html)
                .contains("<title>Parsed Title</title>")
                .contains("<h2>Main &amp; Intro</h2>")
                .contains("<p>Before <b>bold</b> and <i>italic</i> text.</p>")
                .contains("<h3>Details</h3>")
                .contains("class=\"externalLink\"")
                .contains("href=\"https://example.com\"")
                .contains(">site</a>")
                .contains("<a name=\"inside\">anchor</a>")
                .contains("<div class=\"source\"><pre>code &amp; symbols</pre>")
                .contains("<ul><li>first</li>")
                .contains("<li>second</li>")
                .contains("<ol type=\"1\"><li>one</li>")
                .contains("<table class=\"bodyTable\"><tr class=\"a\">")
                .contains("<td>head</td>")
                .contains("<td>cell</td>")
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

        parser.parse(new StringReader(xhtml), new XhtmlSink(writer));

        String html = writer.toString();
        assertThat(html)
                .contains("<title>Project Documentation</title>")
                .contains("<meta name=\"author\" content=\"Documentation Team\" />")
                .contains("<p>content</p>");
    }

    @Test
    void parserClosesNestedSectionsWhenHigherLevelHeadingStarts() throws Exception {
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

        parser.parse(new StringReader(xhtml), new XhtmlSink(writer));

        String html = writer.toString().replaceAll(">\\s+<", "><");
        assertThat(html).contains(
                "<div class=\"section\"><h2>First</h2><p>one</p>"
                        + "<div class=\"section\"><h3>Child</h3><p>two</p></div></div>"
                        + "<div class=\"section\"><h2>Second</h2><p>three</p></div>");
    }

    @Test
    void parserReportsMalformedXmlAsParseException() {
        XhtmlParser parser = new XhtmlParser();

        assertThatThrownBy(() -> parser.parse(new StringReader("<html><body><p>unfinished</body></html>"),
                new XhtmlSink(new StringWriter())))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("Error parsing the model");
    }

    @Test
    void helperClassesExposeEncodingContextAndStringLookupBehavior() {
        Map<String, String> values = new HashMap<>();
        values.put("site.title", "Doxia XHTML");
        StringsMap stringsMap = new StringsMap(values);

        RenderingContext context = new RenderingContext(
                new File("target/site"), "guide/index.xhtml.vm", "xhtml", "xhtml");
        context.setAttribute("skin", "default");

        assertThat(stringsMap.get("site.title")).isEqualTo("Doxia XHTML");
        assertThat(stringsMap.get("missing")).isNull();
        assertThat(context.getBasedir()).isEqualTo(new File("target/site"));
        assertThat(context.getInputName()).isEqualTo("guide/index.xhtml.vm");
        assertThat(context.getOutputName()).isEqualTo("guide/index.html");
        assertThat(context.getParserId()).isEqualTo("xhtml");
        assertThat(context.getExtension()).isEqualTo("xhtml");
        assertThat(context.getRelativePath()).isNotNull();
        assertThat(context.getAttribute("skin")).isEqualTo("default");
        assertThat(context.getAttribute("unknown")).isNull();
        assertThat(XhtmlSink.escapeHTML("<tag attr=\"value\">&</tag>"))
                .isEqualTo("&lt;tag attr=&quot;value&quot;&gt;&amp;&lt;/tag&gt;");
        assertThat(XhtmlSink.encodeURL("folder/My File.xhtml?x=1&y=\u00e4"))
                .isEqualTo("folder/My%20File.xhtml?x=1&y=%c3%a4");
        assertThat(XhtmlSink.encodeURL(null)).isNull();
        assertThat(XhtmlSink.encodeFragment("Hello, Doxia 1.0!")).isEqualTo("hellodoxia10");
    }
}

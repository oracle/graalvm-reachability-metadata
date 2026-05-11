/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_module_apt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.doxia.module.apt.AptParseException;
import org.apache.maven.doxia.module.apt.AptParser;
import org.apache.maven.doxia.module.apt.AptReaderSource;
import org.apache.maven.doxia.module.apt.AptSink;
import org.apache.maven.doxia.module.apt.AptSiteModule;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.SinkAdapter;
import org.junit.jupiter.api.Test;

public class Doxia_module_aptTest {
    @Test
    void parserEmitsStructuredEventsForRichAptDocument() throws Exception {
        String paragraph = " Paragraph with <italic>, <<bold>>, <<<code>>>, {anchor}, "
                + "{{{https://example.test/path}link text}}, escaped \\* star, "
                + "hex \\x41 and unicode \\u03a9.";
        String document = String.join("\n",
                " -----",
                " Demo <Title>",
                " -----",
                " Jane Author",
                " -----",
                " 2007-09-04",
                "",
                "Main Section",
                "",
                paragraph,
                "",
                "* Child Section",
                "",
                " * bullet one",
                "",
                " * bullet two",
                "",
                " [[1]] first numbered",
                "",
                " [[1]] second numbered",
                "",
                " [Term] definition text with non\\ breaking space",
                "",
                "+-----",
                "boxed",
                "  code",
                "+-----",
                "",
                "[images/logo.png] Figure caption",
                "",
                "*--+--:",
                "||Header||Value",
                "*--+--:",
                "|Name|APT",
                "*--+--:",
                "Table caption text",
                "",
                "===",
                "",
                "\f",
                "");
        RecordingSink sink = new RecordingSink();

        new AptParser().parse(new StringReader(document), sink);

        assertThat(sink.events).containsSubsequence(
                "head",
                "title",
                "text:Demo ",
                "italic",
                "text:Title",
                "italic_",
                "title_",
                "author",
                "text:Jane Author",
                "author_",
                "date",
                "text:2007-09-04",
                "date_",
                "head_",
                "body");
        assertThat(sink.events).containsSubsequence(
                "section1",
                "sectionTitle1",
                "text:Main Section",
                "sectionTitle1_",
                "paragraph",
                "text:Paragraph with ",
                "italic",
                "text:italic",
                "italic_",
                "text:, ",
                "bold",
                "text:bold",
                "bold_",
                "text:, ",
                "monospaced",
                "text:code",
                "monospaced_");
        assertThat(sink.events).containsSubsequence(
                "anchor:anchor",
                "text:anchor",
                "anchor_",
                "text:, ",
                "link:https://example.test/path",
                "text:link text",
                "link_");
        assertThat(sink.events).contains("text:, escaped * star, hex A and unicode \u03A9.");
        assertThat(sink.events).containsSubsequence(
                "section2",
                "sectionTitle2",
                "text:Child Section",
                "sectionTitle2_",
                "list",
                "listItem",
                "paragraph",
                "text:bullet one",
                "paragraph_",
                "listItem_",
                "listItem",
                "paragraph",
                "text:bullet two",
                "paragraph_",
                "listItem_",
                "list_");
        assertThat(sink.events).containsSubsequence(
                "numberedList:0",
                "numberedListItem",
                "paragraph",
                "text:first numbered",
                "paragraph_",
                "numberedListItem_",
                "numberedListItem",
                "paragraph",
                "text:second numbered",
                "paragraph_",
                "numberedListItem_",
                "numberedList_");
        assertThat(sink.events).containsSubsequence(
                "definitionList",
                "definitionListItem",
                "definedTerm",
                "text:Term",
                "definedTerm_",
                "definition",
                "paragraph",
                "text:definition text with non",
                "nbsp",
                "text:breaking space",
                "paragraph_",
                "definition_",
                "definitionListItem_",
                "definitionList_");
        assertThat(sink.events).containsSubsequence(
                "verbatim:true",
                "text:boxed\n  code",
                "verbatim_",
                "figure",
                "figureGraphics:images/logo.png",
                "figureCaption",
                "text:Figure caption",
                "figureCaption_",
                "figure_");
        assertThat(sink.events).containsSubsequence(
                "table",
                "tableRows:[1, 2]:true",
                "tableRow",
                "tableHeaderCell",
                "text:Header",
                "tableHeaderCell_",
                "tableHeaderCell",
                "text:Value",
                "tableHeaderCell_",
                "tableRow_",
                "tableRow",
                "tableCell",
                "text:Name",
                "tableCell_",
                "tableCell",
                "text:APT",
                "tableCell_",
                "tableRow_",
                "tableRows_",
                "tableCaption",
                "text:Table caption text",
                "tableCaption_",
                "table_");
        assertThat(sink.events).containsSubsequence("horizontalRule", "section2_", "section1_", "body_");
    }

    @Test
    void parserEmitsEventsForDeepSectionHierarchyAndIgnoresComments() throws Exception {
        String document = String.join("\n",
                "~~ Leading comments are ignored before the first block",
                "Top Section",
                "",
                "~~ Comments between sections do not become text events",
                "* Child Section",
                "",
                "** Grandchild Section",
                "",
                "*** Great Grandchild Section",
                "",
                "**** Leaf Section",
                "",
                " Leaf paragraph.",
                "",
                "~~ Trailing comments are ignored after content",
                "");
        RecordingSink sink = new RecordingSink();

        new AptParser().parse(new StringReader(document), sink);

        assertThat(sink.events).containsSubsequence(
                "head",
                "head_",
                "body",
                "section1",
                "sectionTitle1",
                "text:Top Section",
                "sectionTitle1_",
                "section2",
                "sectionTitle2",
                "text:Child Section",
                "sectionTitle2_",
                "section3",
                "sectionTitle3",
                "text:Grandchild Section",
                "sectionTitle3_",
                "section4",
                "sectionTitle4",
                "text:Great Grandchild Section",
                "sectionTitle4_",
                "section5",
                "sectionTitle5",
                "text:Leaf Section",
                "sectionTitle5_",
                "paragraph",
                "text:Leaf paragraph.",
                "paragraph_",
                "section5_",
                "section4_",
                "section3_",
                "section2_",
                "section1_",
                "body_");
        assertThat(sink.events).allMatch(event -> !event.contains("comment"));
    }

    @Test
    void parserEmitsNestedNumberedListsWithExplicitNumberingStyles() throws Exception {
        String document = String.join("\n",
                "Numbering Styles",
                "",
                " [[a]] lower alpha item",
                "",
                "   [[A]] upper alpha item",
                "",
                "     [[i]] lower roman item",
                "",
                "       [[I]] upper roman item",
                "",
                "");
        RecordingSink sink = new RecordingSink();

        new AptParser().parse(new StringReader(document), sink);

        assertThat(sink.events).containsSubsequence(
                "numberedList:" + Sink.NUMBERING_LOWER_ALPHA,
                "numberedListItem",
                "paragraph",
                "text:lower alpha item",
                "paragraph_",
                "numberedList:" + Sink.NUMBERING_UPPER_ALPHA,
                "numberedListItem",
                "paragraph",
                "text:upper alpha item",
                "paragraph_",
                "numberedList:" + Sink.NUMBERING_LOWER_ROMAN,
                "numberedListItem",
                "paragraph",
                "text:lower roman item",
                "paragraph_",
                "numberedList:" + Sink.NUMBERING_UPPER_ROMAN,
                "numberedListItem",
                "paragraph",
                "text:upper roman item",
                "paragraph_",
                "numberedListItem_",
                "numberedList_",
                "numberedListItem_",
                "numberedList_",
                "numberedListItem_",
                "numberedList_",
                "numberedListItem_",
                "numberedList_");
    }

    @Test
    void parserReportsMarkupErrorsWithSourceLocation() {
        AptParseException exception = assertThrows(AptParseException.class, () -> new AptParser().parse(
                new StringReader("""
                    Broken <<bold
                    """),
                new SinkAdapter()));

        assertThat(exception.getMessage()).contains("missing '>>'");
        assertThat(exception.getFileName()).isEqualTo("");
        assertThat(exception.getLineNumber()).isEqualTo(1);
    }

    @Test
    void aptSinkWritesAptMarkupAndEscapesText() {
        StringWriter writer = new StringWriter();
        AptSink sink = new AptSink(writer);

        sink.head();
        sink.title();
        sink.text("Demo");
        sink.title_();
        sink.author();
        sink.text("Author");
        sink.author_();
        sink.date();
        sink.text("Today");
        sink.date_();
        sink.head_();
        sink.sectionTitle2();
        sink.text("Nested");
        sink.sectionTitle2_();
        sink.paragraph();
        sink.text("reserved * + - < > [ ] { } \\ and \u03A9");
        sink.nonBreakingSpace();
        sink.link("https://example.test/path");
        sink.text("Example");
        sink.link_();
        sink.lineBreak();
        sink.rawText("raw");
        sink.paragraph_();
        sink.list();
        sink.listItem();
        sink.text("bullet");
        sink.listItem_();
        sink.list_();
        sink.verbatim(true);
        sink.text("literal * text");
        sink.verbatim_();
        sink.pageBreak();
        sink.flush();

        String rendered = writer.toString();
        assertThat(rendered).contains(" -----\n Demo\n -----\n Author\n -----\n Today\n -----");
        assertThat(rendered).contains("\n*Nested\n");
        assertThat(rendered).contains("reserved \\* \\+ \\- \\< \\> \\[ \\] \\{ \\} \\\\ and \\u03a9");
        assertThat(rendered).contains("\\ {{{https://example.test/path}Example}}");
        assertThat(rendered).contains("\\\nraw");
        assertThat(rendered).contains("\n * bullet\n");
        assertThat(rendered).contains("\n+------+\nliteral \\* text\n+------+");
        assertThat(rendered).contains("\n\f\n");
        assertThat(AptSink.encodeFragment("a b#c")).doesNotContain(" ");
        assertThat(AptSink.encodeURL("a b?c=d")).doesNotContain(" ");
    }

    @Test
    void readerSourceTracksLinesClosesAtEndAndWrapsReadFailures() throws Exception {
        AptReaderSource source = new AptReaderSource(new StringReader("first\nsecond"));

        assertThat(source.getName()).isEmpty();
        assertThat(source.getLineNumber()).isEqualTo(-1);
        assertThat(source.getNextLine()).isEqualTo("first");
        assertThat(source.getLineNumber()).isEqualTo(1);
        assertThat(source.getNextLine()).isEqualTo("second");
        assertThat(source.getLineNumber()).isEqualTo(2);
        assertThat(source.getNextLine()).isNull();
        assertThat(source.getNextLine()).isNull();

        AptReaderSource failingSource = new AptReaderSource(new FailingReader());
        AptParseException exception = assertThrows(AptParseException.class, failingSource::getNextLine);
        assertThat(exception).hasCauseInstanceOf(IOException.class);
        failingSource.close();
    }

    @Test
    void siteModuleIdentifiesAptSources() {
        AptSiteModule module = new AptSiteModule();

        assertThat(module.getSourceDirectory()).isEqualTo("apt");
        assertThat(module.getExtension()).isEqualTo("apt");
        assertThat(module.getParserId()).isEqualTo("apt");
    }

    private static final class RecordingSink extends SinkAdapter {
        private final List<String> events = new ArrayList<>();

        @Override
        public void head() {
            events.add("head");
        }

        @Override
        public void head_() {
            events.add("head_");
        }

        @Override
        public void body() {
            events.add("body");
        }

        @Override
        public void body_() {
            events.add("body_");
        }

        @Override
        public void title() {
            events.add("title");
        }

        @Override
        public void title_() {
            events.add("title_");
        }

        @Override
        public void author() {
            events.add("author");
        }

        @Override
        public void author_() {
            events.add("author_");
        }

        @Override
        public void date() {
            events.add("date");
        }

        @Override
        public void date_() {
            events.add("date_");
        }

        @Override
        public void section1() {
            events.add("section1");
        }

        @Override
        public void section1_() {
            events.add("section1_");
        }

        @Override
        public void section2() {
            events.add("section2");
        }

        @Override
        public void section2_() {
            events.add("section2_");
        }

        @Override
        public void section3() {
            events.add("section3");
        }

        @Override
        public void section3_() {
            events.add("section3_");
        }

        @Override
        public void section4() {
            events.add("section4");
        }

        @Override
        public void section4_() {
            events.add("section4_");
        }

        @Override
        public void section5() {
            events.add("section5");
        }

        @Override
        public void section5_() {
            events.add("section5_");
        }

        @Override
        public void sectionTitle1() {
            events.add("sectionTitle1");
        }

        @Override
        public void sectionTitle1_() {
            events.add("sectionTitle1_");
        }

        @Override
        public void sectionTitle2() {
            events.add("sectionTitle2");
        }

        @Override
        public void sectionTitle2_() {
            events.add("sectionTitle2_");
        }

        @Override
        public void sectionTitle3() {
            events.add("sectionTitle3");
        }

        @Override
        public void sectionTitle3_() {
            events.add("sectionTitle3_");
        }

        @Override
        public void sectionTitle4() {
            events.add("sectionTitle4");
        }

        @Override
        public void sectionTitle4_() {
            events.add("sectionTitle4_");
        }

        @Override
        public void sectionTitle5() {
            events.add("sectionTitle5");
        }

        @Override
        public void sectionTitle5_() {
            events.add("sectionTitle5_");
        }

        @Override
        public void paragraph() {
            events.add("paragraph");
        }

        @Override
        public void paragraph_() {
            events.add("paragraph_");
        }

        @Override
        public void list() {
            events.add("list");
        }

        @Override
        public void list_() {
            events.add("list_");
        }

        @Override
        public void listItem() {
            events.add("listItem");
        }

        @Override
        public void listItem_() {
            events.add("listItem_");
        }

        @Override
        public void numberedList(int numbering) {
            events.add("numberedList:" + numbering);
        }

        @Override
        public void numberedList_() {
            events.add("numberedList_");
        }

        @Override
        public void numberedListItem() {
            events.add("numberedListItem");
        }

        @Override
        public void numberedListItem_() {
            events.add("numberedListItem_");
        }

        @Override
        public void definitionList() {
            events.add("definitionList");
        }

        @Override
        public void definitionList_() {
            events.add("definitionList_");
        }

        @Override
        public void definitionListItem() {
            events.add("definitionListItem");
        }

        @Override
        public void definitionListItem_() {
            events.add("definitionListItem_");
        }

        @Override
        public void definedTerm() {
            events.add("definedTerm");
        }

        @Override
        public void definedTerm_() {
            events.add("definedTerm_");
        }

        @Override
        public void definition() {
            events.add("definition");
        }

        @Override
        public void definition_() {
            events.add("definition_");
        }

        @Override
        public void verbatim(boolean boxed) {
            events.add("verbatim:" + boxed);
        }

        @Override
        public void verbatim_() {
            events.add("verbatim_");
        }

        @Override
        public void figure() {
            events.add("figure");
        }

        @Override
        public void figure_() {
            events.add("figure_");
        }

        @Override
        public void figureCaption() {
            events.add("figureCaption");
        }

        @Override
        public void figureCaption_() {
            events.add("figureCaption_");
        }

        @Override
        public void figureGraphics(String name) {
            events.add("figureGraphics:" + name);
        }

        @Override
        public void table() {
            events.add("table");
        }

        @Override
        public void table_() {
            events.add("table_");
        }

        @Override
        public void tableRows(int[] justification, boolean grid) {
            events.add("tableRows:" + Arrays.toString(justification) + ":" + grid);
        }

        @Override
        public void tableRows_() {
            events.add("tableRows_");
        }

        @Override
        public void tableRow() {
            events.add("tableRow");
        }

        @Override
        public void tableRow_() {
            events.add("tableRow_");
        }

        @Override
        public void tableCell() {
            events.add("tableCell");
        }

        @Override
        public void tableCell_() {
            events.add("tableCell_");
        }

        @Override
        public void tableHeaderCell() {
            events.add("tableHeaderCell");
        }

        @Override
        public void tableHeaderCell_() {
            events.add("tableHeaderCell_");
        }

        @Override
        public void tableCaption() {
            events.add("tableCaption");
        }

        @Override
        public void tableCaption_() {
            events.add("tableCaption_");
        }

        @Override
        public void horizontalRule() {
            events.add("horizontalRule");
        }

        @Override
        public void pageBreak() {
            events.add("pageBreak");
        }

        @Override
        public void anchor(String name) {
            events.add("anchor:" + name);
        }

        @Override
        public void anchor_() {
            events.add("anchor_");
        }

        @Override
        public void link(String name) {
            events.add("link:" + name);
        }

        @Override
        public void link_() {
            events.add("link_");
        }

        @Override
        public void italic() {
            events.add("italic");
        }

        @Override
        public void italic_() {
            events.add("italic_");
        }

        @Override
        public void bold() {
            events.add("bold");
        }

        @Override
        public void bold_() {
            events.add("bold_");
        }

        @Override
        public void monospaced() {
            events.add("monospaced");
        }

        @Override
        public void monospaced_() {
            events.add("monospaced_");
        }

        @Override
        public void lineBreak() {
            events.add("lineBreak");
        }

        @Override
        public void nonBreakingSpace() {
            events.add("nbsp");
        }

        @Override
        public void text(String text) {
            events.add("text:" + text);
        }
    }

    private static final class FailingReader extends Reader {
        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            throw new IOException("deliberate read failure");
        }

        @Override
        public void close() {
        }
    }
}

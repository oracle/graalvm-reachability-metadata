/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ccil_cowan_tagsoup.tagsoup;

import org.ccil.cowan.tagsoup.AttributesImpl;
import org.ccil.cowan.tagsoup.AutoDetector;
import org.ccil.cowan.tagsoup.Element;
import org.ccil.cowan.tagsoup.ElementType;
import org.ccil.cowan.tagsoup.HTMLSchema;
import org.ccil.cowan.tagsoup.PYXScanner;
import org.ccil.cowan.tagsoup.PYXWriter;
import org.ccil.cowan.tagsoup.Parser;
import org.ccil.cowan.tagsoup.Schema;
import org.ccil.cowan.tagsoup.XMLWriter;
import org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.SAXParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TagsoupTest {
    private static final String XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml";

    @Test
    void parserRepairsMalformedHtmlAndReportsSaxAndLexicalEvents() throws Exception {
        Parser parser = new Parser();
        RecordingHandler handler = new RecordingHandler();
        parser.setContentHandler(handler);
        parser.setProperty(Parser.lexicalHandlerProperty, handler);

        parser.parse(inputSource("""
                <!doctype html>
                <html><head><title>Example</title></head>
                <body><p id=intro class=lead>First <b>bold <i>italic</b> tail &copy;
                <br><img src=x></p><!-- note --></body></html>
                """));

        assertThat(handler.documentStarted).isTrue();
        assertThat(handler.documentEnded).isTrue();
        assertThat(handler.startNames()).containsSequence("html", "head", "title");
        assertThat(handler.startNames()).contains("body", "p", "b", "i", "br", "img");
        assertThat(handler.endNames()).contains("i", "b", "p", "body", "html");
        assertThat(handler.fullText()).contains("Example", "First ", "bold ", "italic", " tail ©");
        assertThat(handler.comments).contains(" note ");

        StartElement p = handler.firstStart("p");
        assertThat(p.uri).isEqualTo(XHTML_NAMESPACE);
        assertThat(p.localName).isEqualTo("p");
        assertThat(p.attributes.getValue("id")).isEqualTo("intro");
        assertThat(p.attributes.getValue("class")).isEqualTo("lead");
    }

    @Test
    void parserFeaturesControlNamespacesBogonsAndInputStreamDetection() throws Exception {
        Parser parser = new Parser();
        parser.setFeature(Parser.namespacesFeature, false);
        parser.setFeature(Parser.ignoreBogonsFeature, true);
        AutoDetector detector = new Utf8AutoDetector();
        parser.setProperty(Parser.autoDetectorProperty, detector);
        RecordingHandler handler = new RecordingHandler();
        parser.setContentHandler(handler);

        byte[] html = "<html><body><custom>ignored</custom><p>kept</p></body></html>".getBytes(StandardCharsets.UTF_8);
        InputSource source = new InputSource(new ByteArrayInputStream(html));
        parser.parse(source);

        assertThat(parser.getFeature(Parser.namespacesFeature)).isFalse();
        assertThat(parser.getFeature(Parser.ignoreBogonsFeature)).isTrue();
        assertThat(parser.getProperty(Parser.autoDetectorProperty)).isSameAs(detector);
        assertThat(handler.startNames()).doesNotContain("custom");
        assertThat(handler.startNames()).contains("html", "body", "p");
        assertThat(handler.firstStart("p").uri).isEmpty();
        assertThat(handler.firstStart("p").localName).isEmpty();
        assertThat(handler.fullText()).contains("kept");
    }

    @Test
    void parserRejectsUnknownFeaturesAndInvalidPropertyTypes() {
        Parser parser = new Parser();

        assertThatThrownBy(() -> parser.getFeature("urn:unknown-feature"))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("Unknown feature");
        assertThatThrownBy(() -> parser.setProperty(Parser.lexicalHandlerProperty, new Object()))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("not a LexicalHandler");
        assertThatThrownBy(() -> parser.setProperty(Parser.schemaProperty, new Object()))
                .isInstanceOf(SAXException.class)
                .hasMessageContaining("not a Schema");
    }

    @Test
    void jaxpFactoryCreatesConfiguredSaxParsers() throws Exception {
        SAXFactoryImpl factory = new SAXFactoryImpl();
        factory.setFeature(Parser.namespacesFeature, false);
        factory.setFeature(Parser.ignoreBogonsFeature, true);

        SAXParser saxParser = factory.newSAXParser();
        XMLReader reader = saxParser.getXMLReader();
        RecordingHandler handler = new RecordingHandler();
        reader.setContentHandler(handler);
        reader.parse(inputSource("<html><body><made-up>ignored</made-up><p>JAXP</p></body></html>"));

        assertThat(saxParser.isNamespaceAware()).isFalse();
        assertThat(reader.getFeature(Parser.ignoreBogonsFeature)).isTrue();
        assertThat(handler.startNames()).contains("html", "body", "p").doesNotContain("made-up");
        assertThat(handler.firstStart("p").localName).isEmpty();
        assertThat(handler.fullText()).contains("JAXP");
    }

    @Test
    void htmlSchemaElementTypesAndElementsExposeHtmlModelAndAttributes() {
        HTMLSchema schema = new HTMLSchema();
        ElementType html = schema.getElementType("HTML");
        ElementType body = schema.getElementType("body");
        ElementType paragraph = schema.getElementType("p");
        ElementType script = schema.getElementType("script");

        assertThat(schema.getURI()).isEqualTo(XHTML_NAMESPACE);
        assertThat(schema.getPrefix()).isEqualTo("html");
        assertThat(schema.rootElementType()).isSameAs(html);
        assertThat(schema.getEntity("copy")).isEqualTo(169);
        assertThat(html.canContain(body)).isTrue();
        assertThat(paragraph.canContain(body)).isFalse();
        assertThat(script.flags() & Schema.F_CDATA).isEqualTo(Schema.F_CDATA);

        Element element = new Element(paragraph, true);
        element.setAttribute("data:id", "NMTOKEN", "  alpha   beta  ");
        element.setAttribute("id", "ID", "intro");
        element.setAttribute("name", "CDATA", "anchor");
        element.setAttribute("xmlns:ignored", "CDATA", "urn:ignored");

        assertThat(element.name()).isEqualTo("p");
        assertThat(element.namespace()).isEqualTo(XHTML_NAMESPACE);
        assertThat(element.atts().getValue("data:id")).isEqualTo("alpha beta");
        assertThat(element.atts().getURI(element.atts().getIndex("data:id"))).isEqualTo("urn:x-prefix:data");
        assertThat(element.atts().getValue("xmlns:ignored")).isNull();

        element.anonymize();
        assertThat(element.atts().getValue("id")).isNull();
        assertThat(element.atts().getValue("name")).isNull();
        assertThat(element.atts().getValue("data:id")).isEqualTo("alpha beta");

        element.preclose();
        assertThat(element.isPreclosed()).isTrue();
    }

    @Test
    void attributesImplementationSupportsMutationLookupAndCopying() {
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("urn:a", "first", "a:first", "CDATA", "one");
        attributes.addAttribute("", "second", "second", "ID", "two");

        assertThat(attributes.getLength()).isEqualTo(2);
        assertThat(attributes.getIndex("urn:a", "first")).isZero();
        assertThat(attributes.getIndex("second")).isEqualTo(1);
        assertThat(attributes.getType("second")).isEqualTo("ID");
        assertThat(attributes.getValue("urn:a", "first")).isEqualTo("one");

        attributes.setValue(0, "changed");
        attributes.setQName(1, "renamed");
        attributes.setType(1, "CDATA");
        assertThat(attributes.getValue("a:first")).isEqualTo("changed");
        assertThat(attributes.getType("renamed")).isEqualTo("CDATA");

        AttributesImpl copy = new AttributesImpl(attributes);
        copy.removeAttribute(0);
        assertThat(copy.getLength()).isEqualTo(1);
        assertThat(copy.getQName(0)).isEqualTo("renamed");
        assertThat(attributes.getLength()).isEqualTo(2);

        copy.clear();
        assertThat(copy.getLength()).isZero();
    }

    @Test
    void xmlWriterSerializesNamespacesEscapedContentEmptyElementsAndComments() throws Exception {
        StringWriter out = new StringWriter();
        XMLWriter writer = new XMLWriter(out);
        writer.setPrefix("urn:test", "t");

        org.xml.sax.helpers.AttributesImpl attributes = new org.xml.sax.helpers.AttributesImpl();
        attributes.addAttribute("", "label", "label", "CDATA", "Tom & Jerry \"<friends>\"");

        writer.startDocument();
        writer.startElement("urn:test", "root", "t:root", attributes);
        writer.dataElement("urn:test", "child", "t:child", new org.xml.sax.helpers.AttributesImpl(),
                "5 < 6 & \"quoted\"");
        writer.emptyElement("urn:test", "empty", "t:empty", new org.xml.sax.helpers.AttributesImpl());
        writer.comment("a--b".toCharArray(), 0, "a--b".length());
        writer.endElement("urn:test", "root", "t:root");
        writer.endDocument();

        assertThat(out.toString()).startsWith("<?xml version=\"1.0\" standalone=\"yes\"?>");
        assertThat(out.toString()).contains("<t:root");
        assertThat(out.toString()).contains("xmlns:t=\"urn:test\"");
        assertThat(out.toString()).contains("label=\"Tom &amp; Jerry &quot;&lt;friends&gt;&quot;\"");
        assertThat(out.toString()).contains("<t:child>5 &lt; 6 &amp; \"quoted\"</t:child>");
        assertThat(out.toString()).contains("<t:empty/>");
        assertThat(out.toString()).contains("<!--a- -b-->");
        assertThat(out.toString()).endsWith("\n");
    }

    @Test
    void pyxWriterConvertsSaxEventsToPyxLines() throws Exception {
        StringWriter out = new StringWriter();
        PYXWriter writer = new PYXWriter(out);
        org.xml.sax.helpers.AttributesImpl attributes = new org.xml.sax.helpers.AttributesImpl();
        attributes.addAttribute("", "name", "name", "CDATA", "value");

        writer.startDocument();
        writer.startElement("", "root", "root", attributes);
        writer.characters("one\n\t\\two".toCharArray(), 0, "one\n\t\\two".length());
        writer.processingInstruction("target", "data");
        writer.endElement("", "root", "root");
        writer.endDocument();

        assertThat(out.toString().lines().toList()).containsExactly(
                "(root",
                "Aname value",
                "-one",
                "-\\n",
                "-\\t\\\\two",
                "?target data",
                ")root");
    }

    @Test
    void pyxScannerCanDriveParserFromPyxEventStreams() throws Exception {
        Parser parser = new Parser();
        PYXScanner scanner = new PYXScanner();
        parser.setProperty(Parser.scannerProperty, scanner);
        RecordingHandler handler = new RecordingHandler();
        parser.setContentHandler(handler);

        String pyx = String.join("\n",
                "(html",
                "(body",
                "(p",
                "Aid pyx-intro",
                "-Hello from PYX",
                "-\\n",
                "-scanner",
                ")p",
                ")body",
                ")html") + "\n";
        parser.parse(inputSource(pyx));

        assertThat(parser.getProperty(Parser.scannerProperty)).isSameAs(scanner);
        assertThat(handler.startNames()).containsExactly("html", "body", "p");
        assertThat(handler.endNames()).containsExactly("p", "body", "html");

        StartElement paragraph = handler.firstStart("p");
        assertThat(paragraph.uri).isEqualTo(XHTML_NAMESPACE);
        assertThat(paragraph.attributes.getValue("id")).isEqualTo("pyx-intro");
        assertThat(handler.fullText()).isEqualTo("Hello from PYX\nscanner");
    }

    @Test
    void parserCanStreamHtmlThroughXmlWriter() throws Exception {
        Parser parser = new Parser();
        StringWriter out = new StringWriter();
        XMLWriter writer = new XMLWriter(out);
        writer.setOutputProperty(XMLWriter.OMIT_XML_DECLARATION, "yes");
        parser.setContentHandler(writer);
        parser.setProperty(Parser.lexicalHandlerProperty, writer);

        parser.parse(inputSource("<html><body><p title='5 > 4'>Fish &amp; chips<br><unknown>kept</body></html>"));

        assertThat(out.toString()).contains("<html");
        assertThat(out.toString()).contains("<body>");
        assertThat(out.toString()).contains("<p title=\"5 &gt; 4\">Fish &amp; chips");
        assertThat(out.toString()).contains("<br");
        assertThat(out.toString()).contains("<unknown>kept</unknown>");
    }

    private static InputSource inputSource(String html) {
        return new InputSource(new StringReader(html));
    }

    private static final class Utf8AutoDetector implements AutoDetector {
        @Override
        public Reader autoDetectingReader(InputStream input) {
            return new InputStreamReader(input, StandardCharsets.UTF_8);
        }
    }

    private static final class RecordingHandler extends DefaultHandler implements LexicalHandler {
        private final List<StartElement> starts = new ArrayList<>();
        private final List<String> ends = new ArrayList<>();
        private final List<String> text = new ArrayList<>();
        private final List<String> comments = new ArrayList<>();
        private boolean documentStarted;
        private boolean documentEnded;

        @Override
        public void startDocument() {
            documentStarted = true;
        }

        @Override
        public void endDocument() {
            documentEnded = true;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            starts.add(new StartElement(uri, localName, qName, new AttributesImpl(attributes)));
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            ends.add(qName);
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            text.add(new String(ch, start, length));
        }

        @Override
        public void comment(char[] ch, int start, int length) {
            comments.add(new String(ch, start, length));
        }

        @Override
        public void startDTD(String name, String publicId, String systemId) {
        }

        @Override
        public void endDTD() {
        }

        @Override
        public void startEntity(String name) {
        }

        @Override
        public void endEntity(String name) {
        }

        @Override
        public void startCDATA() {
        }

        @Override
        public void endCDATA() {
        }

        private List<String> startNames() {
            return starts.stream().map(StartElement::qName).toList();
        }

        private List<String> endNames() {
            return ends;
        }

        private String fullText() {
            return String.join("", text);
        }

        private StartElement firstStart(String qName) {
            return starts.stream()
                    .filter(start -> start.qName().equals(qName))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private record StartElement(String uri, String localName, String qName, AttributesImpl attributes) {
    }
}

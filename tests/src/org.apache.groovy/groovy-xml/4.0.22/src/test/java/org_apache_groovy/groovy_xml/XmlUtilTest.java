/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_xml;

import groovy.lang.Writable;
import groovy.util.Node;
import groovy.xml.DOMBuilder;
import groovy.xml.XmlParser;
import groovy.xml.XmlUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlUtilTest {
    private static final String SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="catalog">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="book" type="xs:string" maxOccurs="unbounded"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @Test
    void serializesParsedNodes() throws Exception {
        Node catalog = new XmlParser().parseText("<catalog><book id=\"b1\">Groovy &amp; XML</book></catalog>");

        String xml = XmlUtil.serialize(catalog);
        StringWriter writer = new StringWriter();
        XmlUtil.serialize(catalog, writer);

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("<book id=\"b1\">Groovy &amp; XML</book>");
        assertThat(writer.toString()).contains("<book id=\"b1\">Groovy &amp; XML</book>");
    }

    @Test
    void serializesDomElements() throws Exception {
        String source = "<catalog><book id=\"b1\"><title>Groovy</title></book></catalog>";
        Element catalog = DOMBuilder.parse(new StringReader(source)).getDocumentElement();

        String xml = XmlUtil.serialize(catalog);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        XmlUtil.serialize(catalog, bytes);

        assertThat(xml).contains("<book id=\"b1\">").contains("<title>Groovy</title>");
        assertThat(bytes.toString(StandardCharsets.UTF_8)).contains("<title>Groovy</title>");
    }

    @Test
    void reformatsXmlStringsAndWritables() {
        Writable writable = writer -> writer.append("<records><record id=\"w1\">Writable</record></records>");

        String fromString = XmlUtil.serialize("<records><record id=\"s1\">Text</record></records>");
        String fromWritable = XmlUtil.serialize(writable);

        assertThat(fromString).contains("<records>").contains("  <record id=\"s1\">Text</record>");
        assertThat(fromWritable).contains("<record id=\"w1\">Writable</record>");
    }

    @Test
    void expandsDoctypeEntitiesWhenAllowed() {
        String withDoctype = "<!DOCTYPE note [<!ENTITY who \"Groovy\">]><note>&who;</note>";

        String xml = XmlUtil.serialize(withDoctype, true);

        assertThat(xml).contains("<note>Groovy</note>");
    }

    @Test
    void escapesMarkupAndControlCharacters() {
        assertThat(XmlUtil.escapeXml("<a href=\"x\">Tom & 'Jerry'</a>"))
                .isEqualTo("&lt;a href=&quot;x&quot;&gt;Tom &amp; &apos;Jerry&apos;&lt;/a&gt;");
        assertThat(XmlUtil.escapeControlCharacters("line1\nline2\tend")).isEqualTo("line1&#10;line2&#9;end");
    }

    @Test
    void createsSchemaValidatingSaxParsers() throws Exception {
        SAXParser parser = XmlUtil.newSAXParser(XMLConstants.W3C_XML_SCHEMA_NS_URI, new StreamSource(new StringReader(SCHEMA)));
        RecordingHandler handler = new RecordingHandler();

        parser.parse(new InputSource(new StringReader("<catalog><book>Groovy</book><book>GraalVM</book></catalog>")), handler);

        assertThat(handler.elements).containsExactly("catalog", "book", "book");
        assertThat(handler.errors).isEmpty();
    }

    @Test
    void reportsDocumentsThatViolateTheSchema() throws Exception {
        SAXParser parser = XmlUtil.newSAXParser(XMLConstants.W3C_XML_SCHEMA_NS_URI, new StreamSource(new StringReader(SCHEMA)));
        RecordingHandler handler = new RecordingHandler();

        parser.parse(new InputSource(new StringReader("<catalog><magazine>Groovy</magazine></catalog>")), handler);

        assertThat(handler.errors).isNotEmpty();
        assertThat(handler.errors.get(0)).contains("magazine");
    }

    private static final class RecordingHandler extends DefaultHandler {
        private final List<String> elements = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            elements.add(localName.isEmpty() ? qName : localName);
        }

        @Override
        public void error(SAXParseException e) {
            errors.add(e.getMessage());
        }
    }
}

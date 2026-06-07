/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import com.ctc.wstx.stax.WstxEventFactory;
import com.ctc.wstx.stax.WstxInputFactory;
import com.ctc.wstx.stax.WstxOutputFactory;
import org.codehaus.stax2.XMLStreamReader2;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Woodstox_coreTest {

    @Test
    void streamReaderHandlesNamespacesDtdCdataCommentsAndProcessingInstructions() throws Exception {
        String xml = """
                <!DOCTYPE doc [<!ENTITY author 'Woodstox'>]>
                <doc xmlns="urn:test" xmlns:p="urn:prefix" p:version="1">
                  <!-- reader note -->
                  <?process value?>
                  <item id="a"><![CDATA[Hello <xml> ]]>&author;</item>
                  <empty/>
                </doc>
                """;

        XMLInputFactory inputFactory = newInputFactory();
        inputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);
        inputFactory.setProperty(XMLInputFactory.IS_COALESCING, false);

        boolean sawComment = false;
        boolean sawProcessingInstruction = false;
        boolean sawItem = false;
        boolean sawEmpty = false;

        XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(xml));
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.COMMENT) {
                    sawComment = true;
                    assertThat(reader.getText()).contains("reader note");
                } else if (event == XMLStreamConstants.PROCESSING_INSTRUCTION) {
                    sawProcessingInstruction = true;
                    assertThat(reader.getPITarget()).isEqualTo("process");
                    assertThat(reader.getPIData()).isEqualTo("value");
                } else if (event == XMLStreamConstants.START_ELEMENT) {
                    if ("doc".equals(reader.getLocalName())) {
                        assertThat(reader.getNamespaceURI()).isEqualTo("urn:test");
                        assertThat(reader.getNamespaceURI("p")).isEqualTo("urn:prefix");
                        assertThat(reader.getAttributeValue("urn:prefix", "version")).isEqualTo("1");
                        assertThat(reader.getNamespaceCount()).isEqualTo(2);
                    } else if ("item".equals(reader.getLocalName())) {
                        sawItem = true;
                        assertThat(reader.getAttributeValue(null, "id")).isEqualTo("a");
                        assertThat(reader.getElementText()).isEqualTo("Hello <xml> Woodstox");
                    } else if ("empty".equals(reader.getLocalName())) {
                        sawEmpty = true;
                    }
                }
            }
        } finally {
            reader.close();
        }

        assertThat(sawComment).isTrue();
        assertThat(sawProcessingInstruction).isTrue();
        assertThat(sawItem).isTrue();
        assertThat(sawEmpty).isTrue();
    }

    @Test
    void streamReaderResolvesExternalEntitiesWithCustomResolver() throws Exception {
        String xml = """
                <!DOCTYPE doc [<!ENTITY external SYSTEM "memory:message">]>
                <doc>&external;</doc>
                """;

        XMLInputFactory inputFactory = newInputFactory();
        inputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, true);
        inputFactory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            assertThat(publicId).isNull();
            assertThat(systemId).isEqualTo("memory:message");
            return new ByteArrayInputStream("resolved external entity".getBytes(StandardCharsets.UTF_8));
        });

        XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(xml));
        try {
            assertThat(nextStartElement(reader)).isEqualTo("doc");
            assertThat(reader.getElementText()).isEqualTo("resolved external entity");
        } finally {
            reader.close();
        }
    }

    @Test
    void streamFilterExposesOnlyAcceptedElements() throws Exception {
        String xml = """
                <catalog>
                  <book id="b1"><title>First</title></book>
                  <magazine id="m1"/>
                  <book id="b2"><title>Second</title></book>
                </catalog>
                """;

        XMLInputFactory inputFactory = newInputFactory();
        XMLStreamReader sourceReader = inputFactory.createXMLStreamReader(new StringReader(xml));
        XMLStreamReader filteredReader = inputFactory.createFilteredReader(sourceReader, reader ->
                reader.getEventType() == XMLStreamConstants.START_ELEMENT
                        && "book".equals(reader.getLocalName()));
        List<String> bookIds = new ArrayList<>();

        try {
            if (filteredReader.getEventType() == XMLStreamConstants.START_ELEMENT) {
                assertThat(filteredReader.getLocalName()).isEqualTo("book");
                bookIds.add(filteredReader.getAttributeValue(null, "id"));
            }
            while (filteredReader.hasNext()) {
                int event = filteredReader.next();
                if (event == XMLStreamConstants.END_DOCUMENT) {
                    break;
                }
                assertThat(event).isEqualTo(XMLStreamConstants.START_ELEMENT);
                assertThat(filteredReader.getLocalName()).isEqualTo("book");
                bookIds.add(filteredReader.getAttributeValue(null, "id"));
            }
        } finally {
            filteredReader.close();
        }

        assertThat(bookIds).containsExactly("b1", "b2");
    }

    @Test
    void stax2ReaderProvidesTypedElementAndAttributeAccess() throws Exception {
        String xml = """
                <values count="3">
                  <integer>42</integer>
                  <flag>true</flag>
                  <decimal>2.5</decimal>
                </values>
                """;

        XMLStreamReader2 reader = (XMLStreamReader2) newInputFactory()
                .createXMLStreamReader(new StringReader(xml));
        try {
            assertThat(nextStartElement(reader)).isEqualTo("values");
            assertThat(reader.getAttributeAsInt(0)).isEqualTo(3);

            assertThat(nextStartElement(reader)).isEqualTo("integer");
            assertThat(reader.getElementAsInt()).isEqualTo(42);

            assertThat(nextStartElement(reader)).isEqualTo("flag");
            assertThat(reader.getElementAsBoolean()).isTrue();

            assertThat(nextStartElement(reader)).isEqualTo("decimal");
            assertThat(reader.getElementAsDouble()).isEqualTo(2.5d);
        } finally {
            reader.close();
        }
    }

    @Test
    void streamWriterProducesWellFormedNamespacedDocument() throws Exception {
        StringWriter output = new StringWriter();
        XMLStreamWriter writer = new WstxOutputFactory().createXMLStreamWriter(output);

        writer.writeStartDocument("UTF-8", "1.0");
        writer.setPrefix("p", "urn:test");
        writer.setPrefix("ex", "urn:extra");
        writer.writeStartElement("p", "doc", "urn:test");
        writer.writeNamespace("p", "urn:test");
        writer.writeNamespace("ex", "urn:extra");
        writer.writeAttribute("ex", "urn:extra", "flag", "true");
        writer.writeComment("writer note");
        writer.writeProcessingInstruction("target", "instruction");
        writer.writeStartElement("p", "child", "urn:test");
        writer.writeCData("Hello <xml> & data");
        writer.writeEndElement();
        writer.writeEmptyElement("p", "empty", "urn:test");
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.close();

        Document document = parseDocument(output.toString());
        Element root = document.getDocumentElement();

        assertThat(root.getLocalName()).isEqualTo("doc");
        assertThat(root.getNamespaceURI()).isEqualTo("urn:test");
        assertThat(root.getAttributeNS("urn:extra", "flag")).isEqualTo("true");
        assertThat(root.getElementsByTagNameNS("urn:test", "child").item(0).getTextContent())
                .isEqualTo("Hello <xml> & data");
        assertThat(root.getElementsByTagNameNS("urn:test", "empty").getLength()).isEqualTo(1);
        assertThat(output.toString()).contains("<!--writer note-->", "<?target instruction?>");
    }

    @Test
    void eventFactoryAndEventReaderRoundTripApplicationEvents() throws Exception {
        WstxEventFactory eventFactory = new WstxEventFactory();
        StringWriter output = new StringWriter();
        XMLEventWriter writer = new WstxOutputFactory().createXMLEventWriter(output);
        List<Attribute> attributes = List.of(eventFactory.createAttribute("id", "42"));
        List<Namespace> namespaces = List.of();

        writer.add(eventFactory.createStartDocument());
        writer.add(eventFactory.createStartElement("", "", "created", attributes.iterator(), namespaces.iterator()));
        writer.add(eventFactory.createCharacters("event text"));
        writer.add(eventFactory.createEndElement("", "", "created"));
        writer.add(eventFactory.createEndDocument());
        writer.close();

        XMLEventReader reader = newInputFactory().createXMLEventReader(new StringReader(output.toString()));
        try {
            StartElement startElement = null;
            Characters characters = null;
            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();
                if (event.isStartElement()) {
                    startElement = event.asStartElement();
                } else if (event.isCharacters() && !event.asCharacters().isWhiteSpace()) {
                    characters = event.asCharacters();
                }
            }

            assertThat(startElement).isNotNull();
            assertThat(startElement.getName().getLocalPart()).isEqualTo("created");
            assertThat(startElement.getAttributeByName(QName.valueOf("id")).getValue()).isEqualTo("42");
            assertThat(characters).isNotNull();
            assertThat(characters.getData()).isEqualTo("event text");
        } finally {
            reader.close();
        }
    }

    @Test
    void dtdValidationAcceptsValidDocumentsAndRejectsInvalidDocuments() throws Exception {
        String validXml = """
                <!DOCTYPE doc [<!ELEMENT doc (item+)> <!ELEMENT item (#PCDATA)>]>
                <doc><item>ok</item></doc>
                """;
        String invalidXml = """
                <!DOCTYPE doc [<!ELEMENT doc (item+)> <!ELEMENT item (#PCDATA)>]>
                <doc><other>not allowed</other></doc>
                """;

        drain(newValidatingInputFactory().createXMLStreamReader(new StringReader(validXml)));

        assertThatThrownBy(() -> drain(newValidatingInputFactory().createXMLStreamReader(new StringReader(invalidXml))))
                .isInstanceOf(XMLStreamException.class);
    }

    @Test
    void streamReaderDetectsXmlDeclarationEncodingFromByteStream() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><message>caf\u00e9</message>";
        byte[] encodedXml = xml.getBytes(StandardCharsets.ISO_8859_1);

        XMLStreamReader reader = newInputFactory().createXMLStreamReader(new ByteArrayInputStream(encodedXml));
        try {
            assertThat(nextStartElement(reader)).isEqualTo("message");
            assertThat(reader.getElementText()).isEqualTo("caf\u00e9");
        } finally {
            reader.close();
        }
    }

    private static XMLInputFactory newInputFactory() {
        XMLInputFactory inputFactory = new WstxInputFactory();
        inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, true);
        return inputFactory;
    }

    private static XMLInputFactory newValidatingInputFactory() {
        XMLInputFactory inputFactory = newInputFactory();
        inputFactory.setProperty(XMLInputFactory.IS_VALIDATING, true);
        return inputFactory;
    }

    private static String nextStartElement(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                return reader.getLocalName();
            }
        }
        throw new XMLStreamException("Expected a start element");
    }

    private static void drain(XMLStreamReader reader) throws XMLStreamException {
        try {
            while (reader.hasNext()) {
                reader.next();
            }
        } finally {
            reader.close();
        }
    }

    private static Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}

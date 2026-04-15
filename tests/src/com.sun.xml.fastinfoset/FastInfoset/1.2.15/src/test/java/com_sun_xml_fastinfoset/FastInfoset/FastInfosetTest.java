/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_fastinfoset.FastInfoset;

import com.sun.xml.fastinfoset.sax.Features;
import com.sun.xml.fastinfoset.sax.SAXDocumentParser;
import com.sun.xml.fastinfoset.sax.SAXDocumentSerializer;
import com.sun.xml.fastinfoset.stax.StAXDocumentParser;
import com.sun.xml.fastinfoset.stax.StAXDocumentSerializer;
import org.jvnet.fastinfoset.FastInfosetResult;
import org.jvnet.fastinfoset.FastInfosetSource;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FastInfosetTest {

    @Test
    void jaxpRoundTripPreservesNamespacesAndDocumentLevelNodes() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!--top-level-->"
                + "<?app keep?>"
                + "<root xmlns=\"urn:root\" xmlns:meta=\"urn:meta\" meta:flag=\"true\">"
                + "<meta:child meta:count=\"2\"><![CDATA[alpha < beta & gamma]]></meta:child>"
                + "<meta:child meta:count=\"3\">delta</meta:child>"
                + "<empty meta:kind=\"marker\"/>"
                + "</root>";

        byte[] fastInfosetDocument = toFastInfoset(xml);
        Document roundTrippedDocument = fromFastInfoset(fastInfosetDocument);

        assertThat(fastInfosetDocument).isNotEmpty();
        assertThat(roundTrippedDocument.getChildNodes().getLength()).isEqualTo(3);

        Node commentNode = roundTrippedDocument.getFirstChild();
        assertThat(commentNode.getNodeType()).isEqualTo(Node.COMMENT_NODE);
        assertThat(commentNode.getNodeValue()).isEqualTo("top-level");

        ProcessingInstruction processingInstruction = (ProcessingInstruction) commentNode.getNextSibling();
        assertThat(processingInstruction.getTarget()).isEqualTo("app");
        assertThat(processingInstruction.getData()).isEqualTo("keep");

        Element rootElement = roundTrippedDocument.getDocumentElement();
        assertThat(rootElement.getNamespaceURI()).isEqualTo("urn:root");
        assertThat(rootElement.getLocalName()).isEqualTo("root");
        assertThat(rootElement.getAttributeNS("urn:meta", "flag")).isEqualTo("true");

        NodeList childElements = rootElement.getElementsByTagNameNS("urn:meta", "child");
        assertThat(childElements.getLength()).isEqualTo(2);
        assertThat(((Element) childElements.item(0)).getAttributeNS("urn:meta", "count")).isEqualTo("2");
        assertThat(childElements.item(0).getTextContent()).isEqualTo("alpha < beta & gamma");
        assertThat(((Element) childElements.item(1)).getAttributeNS("urn:meta", "count")).isEqualTo("3");
        assertThat(childElements.item(1).getTextContent()).isEqualTo("delta");

        Element emptyElement = (Element) rootElement.getElementsByTagNameNS("urn:root", "empty").item(0);
        assertThat(emptyElement.getAttributeNS("urn:meta", "kind")).isEqualTo("marker");
    }

    @Test
    void staxRoundTripPreservesStructuredEvents() throws Exception {
        byte[] fastInfosetDocument = createStructuredStaxDocument();
        StAXDocumentParser reader = new StAXDocumentParser(new ByteArrayInputStream(fastInfosetDocument));

        assertThat(reader.next()).isEqualTo(XMLStreamConstants.COMMENT);
        assertThat(reader.getText()).isEqualTo("preface");

        assertThat(reader.next()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        reader.require(XMLStreamConstants.START_ELEMENT, "urn:default", "root");
        assertThat(reader.getAttributeValue("urn:meta", "status")).isEqualTo("ready");
        assertThat(namespacesAtCurrentElement(reader)).containsEntry("", "urn:default").containsEntry("m", "urn:meta");
        assertThat(reader.getNamespaceContext().getNamespaceURI("m")).isEqualTo("urn:meta");

        assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        reader.require(XMLStreamConstants.START_ELEMENT, "urn:meta", "entry");
        assertThat(reader.getAttributeValue(null, "id")).isEqualTo("42");
        assertThat(reader.getElementText()).isEqualTo("value");

        assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        reader.require(XMLStreamConstants.START_ELEMENT, "urn:meta", "empty");
        assertThat(reader.next()).isEqualTo(XMLStreamConstants.END_ELEMENT);
        assertThat(reader.getLocalName()).isEqualTo("empty");

        assertThat(reader.next()).isEqualTo(XMLStreamConstants.PROCESSING_INSTRUCTION);
        assertThat(reader.getPITarget()).isEqualTo("done");
        assertThat(reader.getPIData()).isEqualTo("now");

        assertThat(reader.next()).isEqualTo(XMLStreamConstants.END_ELEMENT);
        assertThat(reader.getLocalName()).isEqualTo("root");
        assertThat(reader.next()).isEqualTo(XMLStreamConstants.END_DOCUMENT);
    }

    @Test
    void staxReaderExposesBinaryOctetAlgorithmData() throws Exception {
        byte[] fastInfosetDocument = createBinaryStaxDocument();
        StAXDocumentParser reader = new StAXDocumentParser(new ByteArrayInputStream(fastInfosetDocument));

        assertThat(reader.next()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        reader.require(XMLStreamConstants.START_ELEMENT, "urn:binary", "payload");

        assertThat(reader.next()).isEqualTo(XMLStreamConstants.CHARACTERS);
        assertThat(reader.hasTextAlgorithmBytes()).isTrue();
        assertThat(reader.getText()).isEqualTo("AQIDBA==");
        assertThat(reader.getTextAlgorithmIndex()).isEqualTo(1);
        assertThat(reader.getTextAlgorithmLength()).isEqualTo(4);
        assertThat(reader.getTextAlgorithmBytesClone()).containsExactly(1, 2, 3, 4);

        assertThat(reader.next()).isEqualTo(XMLStreamConstants.END_ELEMENT);
        assertThat(reader.getLocalName()).isEqualTo("payload");
        assertThat(reader.next()).isEqualTo(XMLStreamConstants.END_ELEMENT);
        assertThat(reader.getLocalName()).isEqualTo("root");
        assertThat(reader.next()).isEqualTo(XMLStreamConstants.END_DOCUMENT);
    }

    @Test
    void saxParserReportsNamespaceDeclarationAttributesAndLexicalEvents() throws Exception {
        byte[] fastInfosetDocument = createSaxDocument();
        SAXDocumentParser parser = new SAXDocumentParser();
        CapturingSaxHandler handler = new CapturingSaxHandler();

        parser.setFeature(Features.NAMESPACE_PREFIXES_FEATURE, true);
        parser.setContentHandler(handler);
        parser.setLexicalHandler(handler);
        parser.parse(new ByteArrayInputStream(fastInfosetDocument));

        assertThat(handler.comments).containsExactly("preface");
        assertThat(handler.prefixMappings).containsExactly("=urn:default", "m=urn:meta");
        assertThat(handler.endPrefixMappings).containsExactly("m", "");
        assertThat(handler.rootNamespaceDeclarations)
                .containsEntry("xmlns", "urn:default")
                .containsEntry("xmlns:m", "urn:meta");
        assertThat(handler.rootAttributes).containsEntry("m:flag", "true");
        assertThat(handler.elementQNames).containsExactly("root", "m:entry");
        assertThat(handler.processingInstructions).containsExactly("done=now");
        assertThat(handler.textContent).containsExactly("value");
    }

    private static byte[] toFastInfoset(String xml) throws TransformerException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new StreamSource(new StringReader(xml)), new FastInfosetResult(outputStream));
        return outputStream.toByteArray();
    }

    private static Document fromFastInfoset(byte[] fastInfosetDocument) throws Exception {
        Document document = newDocument();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new FastInfosetSource(new ByteArrayInputStream(fastInfosetDocument)), new DOMResult(document));
        return document;
    }

    private static Document newDocument() throws ParserConfigurationException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        return documentBuilderFactory.newDocumentBuilder().newDocument();
    }

    private static byte[] createStructuredStaxDocument() throws XMLStreamException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StAXDocumentSerializer writer = new StAXDocumentSerializer(outputStream);

        writer.writeStartDocument();
        writer.writeComment("preface");
        writer.setDefaultNamespace("urn:default");
        writer.setPrefix("m", "urn:meta");
        writer.writeStartElement("", "root", "urn:default");
        writer.writeDefaultNamespace("urn:default");
        writer.writeNamespace("m", "urn:meta");
        writer.writeAttribute("m", "urn:meta", "status", "ready");
        writer.writeStartElement("m", "entry", "urn:meta");
        writer.writeAttribute("id", "42");
        writer.writeCharacters("value");
        writer.writeEndElement();
        writer.writeEmptyElement("m", "empty", "urn:meta");
        writer.writeProcessingInstruction("done", "now");
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.flush();

        return outputStream.toByteArray();
    }

    private static byte[] createBinaryStaxDocument() throws XMLStreamException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StAXDocumentSerializer writer = new StAXDocumentSerializer(outputStream);

        writer.writeStartDocument();
        writer.setDefaultNamespace("urn:binary");
        writer.writeStartElement("", "root", "urn:binary");
        writer.writeDefaultNamespace("urn:binary");
        writer.writeStartElement("", "payload", "urn:binary");
        writer.writeOctets(new byte[]{1, 2, 3, 4}, 0, 4);
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.flush();

        return outputStream.toByteArray();
    }

    private static byte[] createSaxDocument() throws SAXException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        SAXDocumentSerializer writer = new SAXDocumentSerializer();
        writer.setOutputStream(outputStream);

        AttributesImpl rootAttributes = new AttributesImpl();
        rootAttributes.addAttribute("urn:meta", "flag", "m:flag", "CDATA", "true");

        writer.startDocument();
        writer.comment("preface".toCharArray(), 0, "preface".length());
        writer.startPrefixMapping("", "urn:default");
        writer.startPrefixMapping("m", "urn:meta");
        writer.startElement("urn:default", "root", "root", rootAttributes);
        writer.startElement("urn:meta", "entry", "m:entry", new AttributesImpl());
        writer.characters("value".toCharArray(), 0, "value".length());
        writer.endElement("urn:meta", "entry", "m:entry");
        writer.processingInstruction("done", "now");
        writer.endElement("urn:default", "root", "root");
        writer.endDocument();

        return outputStream.toByteArray();
    }

    private static Map<String, String> namespacesAtCurrentElement(StAXDocumentParser reader) {
        Map<String, String> namespaces = new LinkedHashMap<>();
        for (int index = 0; index < reader.getNamespaceCount(); index++) {
            String prefix = reader.getNamespacePrefix(index);
            namespaces.put(prefix == null ? "" : prefix, reader.getNamespaceURI(index));
        }
        return namespaces;
    }

    private static final class CapturingSaxHandler extends DefaultHandler2 {
        private final List<String> comments = new ArrayList<>();
        private final List<String> prefixMappings = new ArrayList<>();
        private final List<String> endPrefixMappings = new ArrayList<>();
        private final Map<String, String> rootNamespaceDeclarations = new LinkedHashMap<>();
        private final Map<String, String> rootAttributes = new LinkedHashMap<>();
        private final List<String> elementQNames = new ArrayList<>();
        private final List<String> processingInstructions = new ArrayList<>();
        private final List<String> textContent = new ArrayList<>();

        @Override
        public void startPrefixMapping(String prefix, String uri) {
            prefixMappings.add((prefix == null ? "" : prefix) + "=" + uri);
        }

        @Override
        public void endPrefixMapping(String prefix) {
            endPrefixMappings.add(prefix == null ? "" : prefix);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            elementQNames.add(qName);
            if (elementQNames.size() == 1) {
                for (int index = 0; index < attributes.getLength(); index++) {
                    String attributeQName = attributes.getQName(index);
                    if (attributeQName.startsWith("xmlns")) {
                        rootNamespaceDeclarations.put(attributeQName, attributes.getValue(index));
                    }
                    else {
                        rootAttributes.put(attributeQName, attributes.getValue(index));
                    }
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            textContent.add(new String(ch, start, length));
        }

        @Override
        public void processingInstruction(String target, String data) {
            processingInstructions.add(target + "=" + data);
        }

        @Override
        public void comment(char[] ch, int start, int length) {
            comments.add(new String(ch, start, length));
        }
    }
}

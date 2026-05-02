/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xml_apis.xml_apis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.xmlcommons.Version;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.NodeIterator;
import org.w3c.dom.traversal.TreeWalker;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.LocatorImpl;
import org.xml.sax.helpers.NamespaceSupport;
import org.xml.sax.helpers.XMLFilterImpl;

public class Xml_apisTest {
    @Test
    void versionClassReportsXmlCommonsIdentity() {
        String product = Version.getProduct();
        String versionNumber = Version.getVersionNum();
        String version = Version.getVersion();

        assertThat(product).contains("XmlCommons");
        assertThat(versionNumber).contains("1.0");
        assertThat(version).contains(product).contains(versionNumber);
    }

    @Test
    void attributesImplSupportsIndexedAndNamedMutation() {
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("urn:books", "id", "bk:id", "ID", "book-1");
        attributes.addAttribute("", "title", "title", "CDATA", "Reachability");

        assertThat(attributes.getLength()).isEqualTo(2);
        assertThat(attributes.getIndex("urn:books", "id")).isZero();
        assertThat(attributes.getIndex("title")).isOne();
        assertThat(attributes.getType("bk:id")).isEqualTo("ID");
        assertThat(attributes.getValue("", "title")).isEqualTo("Reachability");

        AttributesImpl copy = new AttributesImpl(attributes);
        copy.setURI(1, "urn:books");
        copy.setLocalName(1, "title");
        copy.setQName(1, "bk:title");
        copy.setValue(1, "Native Image");
        copy.removeAttribute(0);

        assertThat(copy.getLength()).isOne();
        assertThat(copy.getURI(0)).isEqualTo("urn:books");
        assertThat(copy.getLocalName(0)).isEqualTo("title");
        assertThat(copy.getQName(0)).isEqualTo("bk:title");
        assertThat(copy.getValue(0)).isEqualTo("Native Image");
        assertThat(attributes.getValue("title")).isEqualTo("Reachability");

        copy.clear();
        assertThat(copy.getLength()).isZero();
    }

    @Test
    void namespaceSupportResolvesNestedPrefixContexts() {
        NamespaceSupport namespaces = new NamespaceSupport();
        namespaces.pushContext();

        assertThat(namespaces.declarePrefix("", "urn:default")).isTrue();
        assertThat(namespaces.declarePrefix("bk", "urn:books")).isTrue();
        assertThat(namespaces.declarePrefix("xml", "urn:not-allowed")).isFalse();
        assertThat(namespaces.getURI("")).isEqualTo("urn:default");
        assertThat(namespaces.getPrefix("urn:books")).isEqualTo("bk");

        assertThat(namespaces.processName("bk:title", new String[3], false))
                .containsExactly("urn:books", "title", "bk:title");
        assertThat(namespaces.processName("chapter", new String[3], false))
                .containsExactly("urn:default", "chapter", "chapter");
        assertThat(namespaces.processName("chapter", new String[3], true))
                .containsExactly("", "chapter", "chapter");
        assertThat(toList(namespaces.getDeclaredPrefixes())).containsExactlyInAnyOrder("", "bk");

        namespaces.pushContext();
        namespaces.declarePrefix("bk", "urn:magazines");
        assertThat(namespaces.processName("bk:title", new String[3], false))
                .containsExactly("urn:magazines", "title", "bk:title");
        assertThat(toList(namespaces.getPrefixes("urn:magazines"))).containsExactly("bk");

        namespaces.popContext();
        assertThat(namespaces.processName("bk:title", new String[3], false))
                .containsExactly("urn:books", "title", "bk:title");
    }

    @Test
    void inputSourceLocatorAndParseExceptionPreserveDocumentLocation() {
        ByteArrayInputStream byteStream = new ByteArrayInputStream("<root/>".getBytes(StandardCharsets.UTF_8));
        StringReader characterStream = new StringReader("<root/>");
        InputSource inputSource = new InputSource();
        inputSource.setByteStream(byteStream);
        inputSource.setCharacterStream(characterStream);
        inputSource.setEncoding(StandardCharsets.UTF_8.name());
        inputSource.setPublicId("public-id");
        inputSource.setSystemId("memory:/root.xml");

        assertThat(inputSource.getByteStream()).isSameAs(byteStream);
        assertThat(inputSource.getCharacterStream()).isSameAs(characterStream);
        assertThat(inputSource.getEncoding()).isEqualTo("UTF-8");
        assertThat(inputSource.getPublicId()).isEqualTo("public-id");
        assertThat(inputSource.getSystemId()).isEqualTo("memory:/root.xml");

        LocatorImpl locator = new LocatorImpl();
        locator.setPublicId(inputSource.getPublicId());
        locator.setSystemId(inputSource.getSystemId());
        locator.setLineNumber(12);
        locator.setColumnNumber(34);
        LocatorImpl locatorCopy = new LocatorImpl(locator);
        SAXParseException exception = new SAXParseException("Invalid element", locatorCopy);

        assertThat(exception.getPublicId()).isEqualTo("public-id");
        assertThat(exception.getSystemId()).isEqualTo("memory:/root.xml");
        assertThat(exception.getLineNumber()).isEqualTo(12);
        assertThat(exception.getColumnNumber()).isEqualTo(34);
    }

    @Test
    void domParserBuildsNavigableNamespaceAwareDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        String xml = "<?xml-stylesheet type='text/xsl' href='inventory.xsl'?>"
                + "<inventory xmlns='urn:inventory' xmlns:i='urn:item'>"
                + "<i:item i:id='a1'><name>Native</name><detail><![CDATA[Image]]></detail></i:item>"
                + "</inventory>";

        Document document = builder.parse(new InputSource(new StringReader(xml)));
        Node firstChild = document.getFirstChild();
        Element root = document.getDocumentElement();
        Element item = (Element) document.getElementsByTagNameNS("urn:item", "item").item(0);
        Element generated = document.createElementNS("urn:item", "i:item");
        generated.setAttributeNS("urn:item", "i:id", "b2");
        generated.appendChild(document.createTextNode("Generated"));
        root.appendChild(generated);

        assertThat(firstChild.getNodeType()).isEqualTo(Node.PROCESSING_INSTRUCTION_NODE);
        ProcessingInstruction instruction = (ProcessingInstruction) firstChild;
        assertThat(instruction.getTarget()).isEqualTo("xml-stylesheet");
        assertThat(root.getNamespaceURI()).isEqualTo("urn:inventory");
        assertThat(item.getLocalName()).isEqualTo("item");
        assertThat(item.getAttributeNS("urn:item", "id")).isEqualTo("a1");
        assertThat(item.getTextContent()).contains("Native").contains("Image");

        NodeList items = document.getElementsByTagNameNS("urn:item", "item");
        assertThat(items.getLength()).isEqualTo(2);
        assertThat(items.item(1).getTextContent()).isEqualTo("Generated");
    }

    @Test
    void domTraversalFiltersAndWalksSelectedElements() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader("""
                <catalog>
                    <section name="fiction">
                        <book id="b1" available="true"><title>Dune</title></book>
                        <book id="b2" available="false"><title>Neuromancer</title></book>
                    </section>
                    <section name="reference">
                        <book id="b3" available="true"><title>XML</title></book>
                    </section>
                </catalog>
                """)));
        DocumentTraversal traversal = (DocumentTraversal) document;

        NodeIterator availableBooks = traversal.createNodeIterator(
                document.getDocumentElement(),
                NodeFilter.SHOW_ELEMENT,
                node -> {
                    Element element = (Element) node;
                    if ("book".equals(element.getTagName()) && "true".equals(element.getAttribute("available"))) {
                        return NodeFilter.FILTER_ACCEPT;
                    }
                    return NodeFilter.FILTER_SKIP;
                },
                true);
        List<String> availableBookIds = new ArrayList<>();
        for (Node node = availableBooks.nextNode(); node != null; node = availableBooks.nextNode()) {
            availableBookIds.add(((Element) node).getAttribute("id"));
        }
        availableBooks.detach();

        TreeWalker sections = traversal.createTreeWalker(
                document.getDocumentElement(),
                NodeFilter.SHOW_ELEMENT,
                node -> "section".equals(((Element) node).getTagName())
                        ? NodeFilter.FILTER_ACCEPT
                        : NodeFilter.FILTER_SKIP,
                true);
        Element firstSection = (Element) sections.firstChild();
        Element secondSection = (Element) sections.nextSibling();

        assertThat(availableBookIds).containsExactly("b1", "b3");
        assertThat(firstSection.getAttribute("name")).isEqualTo("fiction");
        assertThat(secondSection.getAttribute("name")).isEqualTo("reference");
        assertThat(sections.nextSibling()).isNull();
    }

    @Test
    void transformerSourcesAndResultsMoveXmlBetweenStreamAndDomRepresentations() throws Exception {
        String xml = "<root><entry name='one'/><entry name='two'/></root>";
        StreamSource source = new StreamSource(new StringReader(xml));
        source.setPublicId("entries");
        source.setSystemId("memory:/entries.xml");
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        result.setSystemId("memory:/out.xml");

        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(source, result);

        assertThat(source.getReader()).isNotNull();
        assertThat(source.getPublicId()).isEqualTo("entries");
        assertThat(source.getSystemId()).isEqualTo("memory:/entries.xml");
        assertThat(result.getWriter()).isSameAs(writer);
        assertThat(result.getSystemId()).isEqualTo("memory:/out.xml");
        assertThat(writer.toString()).contains("<root>").contains("name=\"one\"").contains("name=\"two\"");

        DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
        documentFactory.setNamespaceAware(true);
        Document document = documentFactory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(writer.toString())));
        DOMSource domSource = new DOMSource(document, "memory:/dom.xml");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(domSource, new StreamResult(output));

        assertThat(domSource.getNode()).isSameAs(document);
        assertThat(domSource.getSystemId()).isEqualTo("memory:/dom.xml");
        assertThat(new String(output.toByteArray(), StandardCharsets.UTF_8)).contains("entry");
    }

    @Test
    void saxTransformerFactoryGeneratesXmlFromSaxEventsAndRoutesTransformationOutputToHandlers() throws Exception {
        TransformerFactory baseFactory = TransformerFactory.newInstance();
        baseFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        assertThat(baseFactory).isInstanceOf(SAXTransformerFactory.class);
        SAXTransformerFactory factory = (SAXTransformerFactory) baseFactory;
        TransformerHandler generator = factory.newTransformerHandler();
        StringWriter writer = new StringWriter();
        generator.setResult(new StreamResult(writer));
        generator.getTransformer().setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "kind", "kind", "CDATA", "generated");

        generator.startDocument();
        generator.startElement("", "report", "report", attributes);
        char[] text = "complete".toCharArray();
        generator.characters(text, 0, text.length);
        generator.endElement("", "report", "report");
        generator.endDocument();

        String generatedXml = writer.toString();
        assertThat(generatedXml)
                .contains("<report")
                .contains("kind=\"generated\"")
                .contains(">complete</report>");

        RecordingSaxHandler handler = new RecordingSaxHandler();
        Transformer identity = factory.newTransformer();
        identity.transform(new StreamSource(new StringReader(generatedXml)), new SAXResult(handler));

        assertThat(handler.events).containsExactly(
                "startDocument",
                "start:report:{kind=generated}",
                "text:complete",
                "end:report",
                "endDocument");
    }

    @Test
    void saxParserReportsElementTextAttributesAndLexicalEvents() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        RecordingSaxHandler handler = new RecordingSaxHandler();
        reader.setContentHandler(handler);
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler);

        String xml = "<root xmlns='urn:root'><item code='x'>alpha<![CDATA[beta]]><!--tail--></item></root>";
        reader.parse(new InputSource(new StringReader(xml)));

        assertThat(handler.events).containsExactly(
                "startDocument",
                "start:root:{}",
                "start:item:{code=x}",
                "text:alpha",
                "startCDATA",
                "text:beta",
                "endCDATA",
                "comment:tail",
                "end:item",
                "end:root",
                "endDocument");
    }

    @Test
    void xmlFilterDelegatesConfigurationToParentAndForwardsEvents() throws Exception {
        RecordingXmlReader parent = new RecordingXmlReader();
        XMLFilterImpl filter = new XMLFilterImpl(parent);
        RecordingSaxHandler contentHandler = new RecordingSaxHandler();
        List<String> resolvedEntities = new ArrayList<>();
        EntityResolver resolver = (publicId, systemId) -> {
            resolvedEntities.add(publicId + ":" + systemId);
            return new InputSource(new StringReader(""));
        };

        filter.setFeature("urn:test:feature", true);
        filter.setProperty("urn:test:property", "configured");
        filter.setEntityResolver(resolver);
        filter.setContentHandler(contentHandler);
        filter.parse(new InputSource(new StringReader("<ignored/>")));

        assertThat(filter.getFeature("urn:test:feature")).isTrue();
        assertThat(filter.getProperty("urn:test:property")).isEqualTo("configured");
        assertThat(parent.contentHandler).isSameAs(filter);
        assertThat(parent.entityResolver).isSameAs(filter);
        assertThat(resolvedEntities).containsExactly("public-id:memory:/entity.dtd");
        assertThat(contentHandler.events).containsExactly(
                "startDocument",
                "start:filtered:{level=api}",
                "text:payload",
                "end:filtered",
                "endDocument");
    }

    private static List<String> toList(Enumeration<String> enumeration) {
        List<String> values = new ArrayList<>();
        while (enumeration.hasMoreElements()) {
            values.add(enumeration.nextElement());
        }
        return values;
    }

    private static final class RecordingSaxHandler extends DefaultHandler implements LexicalHandler {
        private final List<String> events = new ArrayList<>();

        @Override
        public void startDocument() {
            events.add("startDocument");
        }

        @Override
        public void endDocument() {
            events.add("endDocument");
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String name = localName.isEmpty() ? qName : localName;
            events.add("start:" + name + ":" + attributeMap(attributes));
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String name = localName.isEmpty() ? qName : localName;
            events.add("end:" + name);
        }

        @Override
        public void characters(char[] chars, int start, int length) {
            String text = new String(chars, start, length).trim();
            if (!text.isEmpty()) {
                events.add("text:" + text);
            }
        }

        @Override
        public void startCDATA() {
            events.add("startCDATA");
        }

        @Override
        public void endCDATA() {
            events.add("endCDATA");
        }

        @Override
        public void comment(char[] chars, int start, int length) {
            events.add("comment:" + new String(chars, start, length));
        }

        @Override
        public void startDTD(String name, String publicId, String systemId) {
            events.add("startDTD:" + name);
        }

        @Override
        public void endDTD() {
            events.add("endDTD");
        }

        @Override
        public void startEntity(String name) {
            events.add("startEntity:" + name);
        }

        @Override
        public void endEntity(String name) {
            events.add("endEntity:" + name);
        }

        private String attributeMap(Attributes attributes) {
            if (attributes.getLength() == 0) {
                return Collections.emptyMap().toString();
            }
            List<String> entries = new ArrayList<>();
            for (int index = 0; index < attributes.getLength(); index++) {
                String localName = attributes.getLocalName(index);
                String name = localName.isEmpty() ? attributes.getQName(index) : localName;
                entries.add(name + "=" + attributes.getValue(index));
            }
            Collections.sort(entries);
            return "{" + String.join(", ", entries) + "}";
        }
    }

    private static final class RecordingXmlReader implements XMLReader {
        private final Map<String, Boolean> features = new HashMap<>();
        private final Map<String, Object> properties = new HashMap<>();
        private EntityResolver entityResolver;
        private DTDHandler dtdHandler;
        private ContentHandler contentHandler = new DefaultHandler();
        private ErrorHandler errorHandler;

        @Override
        public boolean getFeature(String name) throws SAXNotRecognizedException {
            if (!features.containsKey(name)) {
                throw new SAXNotRecognizedException(name);
            }
            return features.get(name);
        }

        @Override
        public void setFeature(String name, boolean value) {
            features.put(name, value);
        }

        @Override
        public Object getProperty(String name) throws SAXNotRecognizedException {
            if (!properties.containsKey(name)) {
                throw new SAXNotRecognizedException(name);
            }
            return properties.get(name);
        }

        @Override
        public void setProperty(String name, Object value) {
            properties.put(name, value);
        }

        @Override
        public void setEntityResolver(EntityResolver resolver) {
            this.entityResolver = resolver;
        }

        @Override
        public EntityResolver getEntityResolver() {
            return entityResolver;
        }

        @Override
        public void setDTDHandler(DTDHandler handler) {
            this.dtdHandler = handler;
        }

        @Override
        public DTDHandler getDTDHandler() {
            return dtdHandler;
        }

        @Override
        public void setContentHandler(ContentHandler handler) {
            this.contentHandler = handler;
        }

        @Override
        public ContentHandler getContentHandler() {
            return contentHandler;
        }

        @Override
        public void setErrorHandler(ErrorHandler handler) {
            this.errorHandler = handler;
        }

        @Override
        public ErrorHandler getErrorHandler() {
            return errorHandler;
        }

        @Override
        public void parse(InputSource input) throws IOException, SAXException {
            if (entityResolver != null) {
                entityResolver.resolveEntity("public-id", "memory:/entity.dtd");
            }
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "level", "level", "CDATA", "api");
            contentHandler.startDocument();
            contentHandler.startElement("", "filtered", "filtered", attributes);
            char[] payload = "payload".toCharArray();
            contentHandler.characters(payload, 0, payload.length);
            contentHandler.endElement("", "filtered", "filtered");
            contentHandler.endDocument();
        }

        @Override
        public void parse(String systemId) throws IOException, SAXException {
            parse(new InputSource(systemId));
        }
    }
}

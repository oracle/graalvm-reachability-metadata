/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package stax.stax_api;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.EventFilter;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.Location;
import javax.xml.stream.StreamFilter;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLReporter;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.Comment;
import javax.xml.stream.events.DTD;
import javax.xml.stream.events.EndDocument;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.EntityDeclaration;
import javax.xml.stream.events.EntityReference;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.NotationDeclaration;
import javax.xml.stream.events.ProcessingInstruction;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.util.EventReaderDelegate;
import javax.xml.stream.util.StreamReaderDelegate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Stax_apiTest {
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<?setup enabled='true'?>"
            + "<root xmlns=\"urn:books\" xmlns:a=\"urn:author\" a:id=\"42\">"
            + "<title>Graal<![CDATA[VM]]></title><empty /></root>";

    @Test
    void streamReaderExposesNamespacesAttributesTextAndFiltering() throws Exception {
        XMLInputFactory factory = newInputFactory();
        List<String> reports = new ArrayList<>();
        XMLResolver resolver = (publicId, systemId, baseUri, namespace) -> new StringReader("");
        XMLReporter reporter = (message, errorType, relatedInformation, location) -> reports.add(message);
        factory.setXMLResolver(resolver);
        factory.setXMLReporter(reporter);

        assertThat(factory.getXMLResolver()).isSameAs(resolver);
        assertThat(factory.getXMLReporter()).isSameAs(reporter);
        assertThat(factory.isPropertySupported(XMLInputFactory.IS_NAMESPACE_AWARE)).isTrue();
        assertThat(factory.getProperty(XMLInputFactory.IS_NAMESPACE_AWARE)).isEqualTo(Boolean.TRUE);

        XMLStreamReader reader = factory.createXMLStreamReader("memory.xml", new StringReader(XML));
        assertThat(reader.getEventType()).isEqualTo(XMLStreamConstants.START_DOCUMENT);
        assertThat(reader.getVersion()).isEqualTo("1.0");

        assertThat(reader.next()).isEqualTo(XMLStreamConstants.PROCESSING_INSTRUCTION);
        assertThat(reader.getPITarget()).isEqualTo("setup");
        assertThat(reader.getPIData()).isEqualTo("enabled='true'");

        assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        reader.require(XMLStreamConstants.START_ELEMENT, "urn:books", "root");
        assertThat(reader.isStartElement()).isTrue();
        assertThat(reader.hasName()).isTrue();
        assertThat(reader.getName()).isEqualTo(new QName("urn:books", "root"));
        assertThat(reader.getLocalName()).isEqualTo("root");
        assertThat(reader.getNamespaceURI()).isEqualTo("urn:books");
        assertThat(reader.getNamespaceURI("a")).isEqualTo("urn:author");
        assertThat(reader.getNamespaceContext().getNamespaceURI("a")).isEqualTo("urn:author");
        assertThat(reader.getAttributeCount()).isEqualTo(1);
        assertThat(reader.getAttributeName(0)).isEqualTo(new QName("urn:author", "id", "a"));
        assertThat(reader.getAttributeNamespace(0)).isEqualTo("urn:author");
        assertThat(reader.getAttributeLocalName(0)).isEqualTo("id");
        assertThat(reader.getAttributePrefix(0)).isEqualTo("a");
        assertThat(reader.getAttributeType(0)).isEqualTo("CDATA");
        assertThat(reader.getAttributeValue(0)).isEqualTo("42");
        assertThat(reader.getAttributeValue("urn:author", "id")).isEqualTo("42");
        assertThat(reader.isAttributeSpecified(0)).isTrue();
        assertThat(reader.getNamespaceCount()).isEqualTo(2);

        assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        assertThat(reader.getLocalName()).isEqualTo("title");
        assertThat(reader.next()).isEqualTo(XMLStreamConstants.CHARACTERS);
        assertThat(reader.isCharacters()).isTrue();
        assertThat(reader.hasText()).isTrue();
        assertThat(reader.getText()).isEqualTo("GraalVM");
        char[] textBuffer = new char[reader.getTextLength()];
        int copied = reader.getTextCharacters(0, textBuffer, 0, textBuffer.length);
        assertThat(new String(textBuffer, 0, copied)).isEqualTo("GraalVM");
        assertThat(reader.getTextCharacters()).containsSubsequence("GraalVM".toCharArray());
        assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.END_ELEMENT);
        assertThat(reader.getLocalName()).isEqualTo("title");

        assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        assertThat(reader.getLocalName()).isEqualTo("empty");
        assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.END_ELEMENT);
        assertThat(reader.getLocalName()).isEqualTo("empty");
        assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.END_ELEMENT);
        assertThat(reader.getLocalName()).isEqualTo("root");
        reader.close();
        assertThat(reports).isEmpty();

        XMLStreamReader filtered = factory.createFilteredReader(
                factory.createXMLStreamReader(new StringReader(XML)),
                elementOnlyStreamFilter());
        List<String> elementEvents = new ArrayList<>();
        addElementEvent(filtered, elementEvents);
        while (filtered.hasNext()) {
            filtered.next();
            addElementEvent(filtered, elementEvents);
        }
        filtered.close();

        assertThat(elementEvents).containsExactly(
                "START:root",
                "START:title",
                "END:title",
                "START:empty",
                "END:empty",
                "END:root");
    }

    @Test
    void eventReaderExposesTypedEventsAndEventFiltering() throws Exception {
        XMLInputFactory factory = newInputFactory();
        XMLEventReader reader = factory.createXMLEventReader(new StringReader(XML));

        XMLEvent startDocument = reader.peek();
        assertThat(startDocument.isStartDocument()).isTrue();
        assertThat(reader.nextEvent().isStartDocument()).isTrue();

        XMLEvent processingInstruction = reader.nextEvent();
        assertThat(processingInstruction.isProcessingInstruction()).isTrue();
        assertThat(((ProcessingInstruction) processingInstruction).getTarget()).isEqualTo("setup");

        StartElement root = reader.nextTag().asStartElement();
        assertThat(root.getName()).isEqualTo(new QName("urn:books", "root"));
        assertThat(root.getNamespaceURI("a")).isEqualTo("urn:author");
        assertThat(root.getAttributeByName(new QName("urn:author", "id", "a")).getValue()).isEqualTo("42");
        assertThat(toList(root.getNamespaces())).hasSize(2);

        StartElement title = reader.nextTag().asStartElement();
        assertThat(title.getName()).isEqualTo(new QName("urn:books", "title"));
        assertThat(reader.getElementText()).isEqualTo("GraalVM");
        assertThat(reader.nextTag().asStartElement().getName()).isEqualTo(new QName("urn:books", "empty"));
        assertThat(reader.nextTag().asEndElement().getName()).isEqualTo(new QName("urn:books", "empty"));
        assertThat(reader.nextTag().asEndElement().getName()).isEqualTo(new QName("urn:books", "root"));
        reader.close();

        XMLEventReader filtered = factory.createFilteredReader(
                factory.createXMLEventReader(new StringReader("<root><a>A</a><b>B</b></root>")),
                startElementOrTextFilter());
        List<String> events = new ArrayList<>();
        while (filtered.hasNext()) {
            XMLEvent event = filtered.nextEvent();
            if (event.isStartElement()) {
                events.add("START:" + event.asStartElement().getName().getLocalPart());
            } else if (event.isCharacters()) {
                events.add("TEXT:" + event.asCharacters().getData());
            }
        }
        filtered.close();

        assertThat(events).containsExactly("START:root", "START:a", "TEXT:A", "START:b", "TEXT:B");
    }

    @Test
    void eventReaderExposesDtdDeclarationsAndEntityReferences() throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        assertThat(factory.isPropertySupported(XMLInputFactory.SUPPORT_DTD)).isTrue();
        assertThat(factory.isPropertySupported(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES)).isTrue();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.TRUE);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);

        String xml = "<!DOCTYPE root ["
                + "<!NOTATION plain SYSTEM 'text/plain'>"
                + "<!ENTITY logo SYSTEM 'logo.txt' NDATA plain>"
                + "<!ENTITY author 'Ada'>"
                + "]><root>&author;</root>";
        XMLEventReader reader = factory.createXMLEventReader(new StringReader(xml));

        DTD dtd = null;
        EntityReference reference = null;
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.getEventType() == XMLStreamConstants.DTD) {
                dtd = (DTD) event;
            } else if (event.isEntityReference()) {
                reference = (EntityReference) event;
            }
        }
        reader.close();

        assertThat(dtd).isNotNull();
        assertThat(dtd.getDocumentTypeDeclaration()).contains("ENTITY author", "NOTATION plain");
        List<EntityDeclaration> entities = dtd.getEntities();
        EntityDeclaration author = findEntity(entities, "author");
        EntityDeclaration logo = findEntity(entities, "logo");
        assertThat(author.getReplacementText()).isEqualTo("Ada");
        assertThat(author.getNotationName()).isNull();
        assertThat(logo.getSystemId()).isEqualTo("logo.txt");
        assertThat(logo.getNotationName()).isEqualTo("plain");

        List<NotationDeclaration> notations = dtd.getNotations();
        assertThat(notations).hasSize(1);
        assertThat(notations.get(0).getName()).isEqualTo("plain");
        assertThat(notations.get(0).getSystemId()).isEqualTo("text/plain");
        assertThat(reference).isNotNull();
        assertThat(reference.getName()).isEqualTo("author");
        assertThat(reference.getDeclaration().getReplacementText()).isEqualTo("Ada");
    }

    @Test
    void eventFactoryCreatesDocumentElementTextAndMiscellaneousEvents() throws Exception {
        XMLEventFactory factory = XMLEventFactory.newFactory();
        FixedLocation location = new FixedLocation(7, 11, 123, "events.xml", "pub");
        factory.setLocation(location);

        QName attributeName = new QName("urn:author", "id", "a");
        Attribute attribute = factory.createAttribute(attributeName, "42");
        Namespace namespace = factory.createNamespace("a", "urn:author");
        Namespace defaultNamespace = factory.createNamespace("urn:books");
        StartElement start = factory.createStartElement(
                new QName("urn:books", "book"),
                Arrays.asList(attribute).iterator(),
                Arrays.asList(namespace, defaultNamespace).iterator());
        EndElement end = factory.createEndElement(
                new QName("urn:books", "book"),
                Arrays.asList(namespace, defaultNamespace).iterator());
        Characters text = factory.createCharacters("Graal & Native");
        Characters cdata = factory.createCData("<sample>");
        Characters space = factory.createSpace("   ");
        Characters ignorableSpace = factory.createIgnorableSpace("\n");
        Comment comment = factory.createComment("checked");
        ProcessingInstruction processingInstruction = factory.createProcessingInstruction("target", "data");
        DTD dtd = factory.createDTD("<!ELEMENT book (#PCDATA)>");
        StartDocument startDocument = factory.createStartDocument("UTF-8", "1.0", true);
        EndDocument endDocument = factory.createEndDocument();

        assertThat(attribute.isAttribute()).isTrue();
        assertThat(attribute.getName()).isEqualTo(attributeName);
        assertThat(attribute.getValue()).isEqualTo("42");
        assertThat(attribute.getDTDType()).isEqualTo("CDATA");
        assertThat(attribute.isSpecified()).isFalse();
        assertThat(namespace.isNamespace()).isTrue();
        assertThat(namespace.getPrefix()).isEqualTo("a");
        assertThat(namespace.getNamespaceURI()).isEqualTo("urn:author");
        assertThat(defaultNamespace.isDefaultNamespaceDeclaration()).isTrue();
        assertThat(start.getName()).isEqualTo(new QName("urn:books", "book"));
        assertThat(start.getAttributeByName(attributeName).getValue()).isEqualTo("42");
        assertThat(toList(start.getNamespaces())).contains(namespace, defaultNamespace);
        assertThat(start.asStartElement()).isSameAs(start);
        assertThat(start.getLocation().getLineNumber()).isEqualTo(7);
        assertThat(end.asEndElement().getName()).isEqualTo(new QName("urn:books", "book"));
        assertThat(text.isCharacters()).isTrue();
        assertThat(text.asCharacters().getData()).isEqualTo("Graal & Native");
        assertThat(cdata.isCData()).isTrue();
        assertThat(space.isWhiteSpace()).isTrue();
        assertThat(ignorableSpace.isIgnorableWhiteSpace()).isTrue();
        assertThat(comment.getText()).isEqualTo("checked");
        assertThat(processingInstruction.getTarget()).isEqualTo("target");
        assertThat(processingInstruction.getData()).isEqualTo("data");
        assertThat(dtd.getDocumentTypeDeclaration()).contains("ELEMENT book");
        assertThat(startDocument.getCharacterEncodingScheme()).isEqualTo("UTF-8");
        assertThat(startDocument.getVersion()).isEqualTo("1.0");
        assertThat(startDocument.encodingSet()).isTrue();
        assertThat(startDocument.isStandalone()).isTrue();
        assertThat(startDocument.standaloneSet()).isTrue();
        assertThat(endDocument.isEndDocument()).isTrue();

        StringWriter encoded = new StringWriter();
        start.writeAsEncodedUnicode(encoded);
        text.writeAsEncodedUnicode(encoded);
        cdata.writeAsEncodedUnicode(encoded);
        comment.writeAsEncodedUnicode(encoded);
        processingInstruction.writeAsEncodedUnicode(encoded);
        end.writeAsEncodedUnicode(encoded);

        assertThat(encoded.toString())
                .contains("book")
                .contains("a:id")
                .contains("42")
                .contains("Graal &amp; Native")
                .contains("<![CDATA[<sample>]]>")
                .contains("<!--checked-->")
                .contains("<?target data?>");
    }

    @Test
    void eventFactoryStartElementResolvesNamespacesFromCustomContext() {
        XMLEventFactory factory = XMLEventFactory.newFactory();
        NamespaceContext namespaceContext = new FixedNamespaceContext();

        StartElement element = factory.createStartElement(
                "b",
                "urn:books",
                "book",
                Collections.emptyIterator(),
                Collections.emptyIterator(),
                namespaceContext);

        assertThat(element.getName()).isEqualTo(new QName("urn:books", "book", "b"));
        assertThat(element.getNamespaceContext()).isSameAs(namespaceContext);
        assertThat(element.getNamespaceURI("b")).isEqualTo("urn:books");
        assertThat(element.getNamespaceURI(XMLConstants.DEFAULT_NS_PREFIX)).isEqualTo("urn:default");
        assertThat(element.getNamespaceContext().getPrefix("urn:authors")).isEqualTo("a");
        assertThat(toList(element.getNamespaceContext().getPrefixes("urn:books"))).containsExactly("b");
        assertThat(element.getNamespaceContext().getNamespaceURI(XMLConstants.XML_NS_PREFIX))
                .isEqualTo(XMLConstants.XML_NS_URI);
        assertThat(element.getNamespaceContext().getNamespaceURI(XMLConstants.XMLNS_ATTRIBUTE))
                .isEqualTo(XMLConstants.XMLNS_ATTRIBUTE_NS_URI);
    }

    @Test
    void outputFactoriesWriteStreamAndEventDocuments() throws Exception {
        XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
        if (outputFactory.isPropertySupported(XMLOutputFactory.IS_REPAIRING_NAMESPACES)) {
            outputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);
            assertThat(outputFactory.getProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES)).isEqualTo(Boolean.TRUE);
        }

        StringWriter streamDocument = new StringWriter();
        XMLStreamWriter streamWriter = outputFactory.createXMLStreamWriter(streamDocument);
        streamWriter.writeStartDocument("UTF-8", "1.0");
        streamWriter.writeStartElement("b", "book", "urn:books");
        streamWriter.writeNamespace("b", "urn:books");
        streamWriter.writeAttribute("b", "urn:books", "id", "42");
        streamWriter.writeStartElement("title");
        streamWriter.writeCharacters("Graal & Native");
        streamWriter.writeEndElement();
        streamWriter.writeCData("<xml-fragment>");
        streamWriter.writeComment("verified");
        streamWriter.writeProcessingInstruction("review", "status='ok'");
        streamWriter.writeEmptyElement("empty");
        streamWriter.writeEndElement();
        streamWriter.writeEndDocument();
        streamWriter.flush();
        streamWriter.close();

        assertThat(streamDocument.toString())
                .contains("<b:book")
                .contains("b:id=\"42\"")
                .contains("Graal &amp; Native")
                .contains("<![CDATA[<xml-fragment>]]>")
                .contains("<!--verified-->")
                .contains("<?review status='ok'?>");

        XMLStreamReader parsed = newInputFactory().createXMLStreamReader(new StringReader(streamDocument.toString()));
        assertThat(parsed.nextTag()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        assertThat(parsed.getName()).isEqualTo(new QName("urn:books", "book", "b"));
        assertThat(parsed.getAttributeValue("urn:books", "id")).isEqualTo("42");
        parsed.close();

        XMLEventFactory eventFactory = XMLEventFactory.newFactory();
        StringWriter eventDocument = new StringWriter();
        XMLEventWriter eventWriter = outputFactory.createXMLEventWriter(eventDocument);
        eventWriter.add(eventFactory.createStartDocument("UTF-8", "1.0"));
        eventWriter.add(eventFactory.createStartElement("", "", "events"));
        eventWriter.add(eventFactory.createCharacters("payload"));
        eventWriter.add(eventFactory.createEndElement("", "", "events"));
        eventWriter.add(eventFactory.createEndDocument());
        eventWriter.flush();
        eventWriter.close();

        assertThat(eventDocument.toString()).contains("events").contains("payload");
    }

    @Test
    void readerDelegatesForwardToTheirParents() throws Exception {
        XMLInputFactory factory = newInputFactory();
        XMLStreamReader streamParent = factory.createXMLStreamReader(
                new StringReader("<root xmlns='urn:test'><child id='1'>value</child></root>"));
        StreamReaderDelegate streamDelegate = new StreamReaderDelegate();
        streamDelegate.setParent(streamParent);

        assertThat(streamDelegate.getParent()).isSameAs(streamParent);
        assertThat(streamDelegate.nextTag()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        assertThat(streamDelegate.getNamespaceURI()).isEqualTo("urn:test");
        assertThat(streamDelegate.nextTag()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        assertThat(streamDelegate.getName()).isEqualTo(new QName("urn:test", "child"));
        assertThat(streamDelegate.getAttributeValue(null, "id")).isEqualTo("1");
        assertThat(streamDelegate.getElementText()).isEqualTo("value");
        assertThat(streamDelegate.nextTag()).isEqualTo(XMLStreamConstants.END_ELEMENT);
        assertThat(streamDelegate.getLocalName()).isEqualTo("root");
        streamDelegate.close();

        XMLEventReader eventParent = factory.createXMLEventReader(
                new StringReader("<root><child>value</child></root>"));
        EventReaderDelegate eventDelegate = new EventReaderDelegate(eventParent);

        assertThat(eventDelegate.getParent()).isSameAs(eventParent);
        assertThat(eventDelegate.peek().isStartDocument()).isTrue();
        assertThat(eventDelegate.nextTag().asStartElement().getName()).isEqualTo(new QName("root"));
        assertThat(eventDelegate.nextTag().asStartElement().getName()).isEqualTo(new QName("child"));
        assertThat(eventDelegate.getElementText()).isEqualTo("value");
        assertThat(eventDelegate.nextTag().asEndElement().getName()).isEqualTo(new QName("root"));
        eventDelegate.close();
    }

    @Test
    void qNameLocationsAndExceptionsExposeTheirPublicState() {
        QName qualified = new QName("urn:test", "local", "p");
        QName sameQualified = QName.valueOf("{urn:test}local");
        QName unqualified = new QName("localOnly");

        assertThat(qualified.getNamespaceURI()).isEqualTo("urn:test");
        assertThat(qualified.getLocalPart()).isEqualTo("local");
        assertThat(qualified.getPrefix()).isEqualTo("p");
        assertThat(qualified).isEqualTo(sameQualified);
        assertThat(qualified.hashCode()).isEqualTo(sameQualified.hashCode());
        assertThat(qualified.toString()).isEqualTo("{urn:test}local");
        assertThat(unqualified.getNamespaceURI()).isEqualTo(XMLConstants.NULL_NS_URI);
        assertThat(unqualified.getPrefix()).isEqualTo(XMLConstants.DEFAULT_NS_PREFIX);
        assertThat(QName.valueOf("plain")).isEqualTo(new QName("plain"));

        FixedLocation location = new FixedLocation(3, 5, 99, "system.xml", "public-id");
        IOException nested = new IOException("disk");
        XMLStreamException streamException = new XMLStreamException("broken", location, nested);
        assertThat(streamException.getNestedException()).isSameAs(nested);
        assertThat(streamException.getLocation()).isSameAs(location);
        assertThat(streamException.getMessage()).contains("broken");

        FactoryConfigurationError configurationError = new FactoryConfigurationError("factory failed", nested);
        assertThat(configurationError.getException()).isSameAs(nested);
        assertThat(configurationError.getCause()).isSameAs(nested);
        assertThat(configurationError.getMessage()).contains("factory failed");
    }

    private static XMLInputFactory newInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        if (factory.isPropertySupported(XMLInputFactory.IS_COALESCING)) {
            factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        }
        if (factory.isPropertySupported(XMLInputFactory.SUPPORT_DTD)) {
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        }
        if (factory.isPropertySupported(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES)) {
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        }
        return factory;
    }

    private static StreamFilter elementOnlyStreamFilter() {
        return reader -> reader.isStartElement() || reader.isEndElement();
    }

    private static EventFilter startElementOrTextFilter() {
        return event -> event.isStartElement() || (event.isCharacters() && !event.asCharacters().isWhiteSpace());
    }

    private static void addElementEvent(XMLStreamReader reader, List<String> elementEvents) {
        int eventType = reader.getEventType();
        if (eventType == XMLStreamConstants.START_ELEMENT || eventType == XMLStreamConstants.END_ELEMENT) {
            elementEvents.add(eventName(eventType) + ":" + reader.getLocalName());
        }
    }

    private static String eventName(int eventType) {
        if (eventType == XMLStreamConstants.START_ELEMENT) {
            return "START";
        }
        if (eventType == XMLStreamConstants.END_ELEMENT) {
            return "END";
        }
        return "OTHER";
    }

    private static EntityDeclaration findEntity(List<EntityDeclaration> entities, String name) {
        return entities.stream()
                .filter(entity -> name.equals(entity.getName()))
                .findFirst()
                .orElseThrow();
    }

    private static <T> List<T> toList(Iterator<T> iterator) {
        List<T> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    private static final class FixedNamespaceContext implements NamespaceContext {
        @Override
        public String getNamespaceURI(String prefix) {
            if (prefix == null) {
                throw new IllegalArgumentException("Prefix must not be null");
            }
            if (XMLConstants.XML_NS_PREFIX.equals(prefix)) {
                return XMLConstants.XML_NS_URI;
            }
            if (XMLConstants.XMLNS_ATTRIBUTE.equals(prefix)) {
                return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
            }
            if (XMLConstants.DEFAULT_NS_PREFIX.equals(prefix)) {
                return "urn:default";
            }
            if ("b".equals(prefix)) {
                return "urn:books";
            }
            if ("a".equals(prefix)) {
                return "urn:authors";
            }
            return XMLConstants.NULL_NS_URI;
        }

        @Override
        public String getPrefix(String namespaceURI) {
            if (namespaceURI == null) {
                throw new IllegalArgumentException("Namespace URI must not be null");
            }
            if (XMLConstants.XML_NS_URI.equals(namespaceURI)) {
                return XMLConstants.XML_NS_PREFIX;
            }
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespaceURI)) {
                return XMLConstants.XMLNS_ATTRIBUTE;
            }
            if ("urn:default".equals(namespaceURI)) {
                return XMLConstants.DEFAULT_NS_PREFIX;
            }
            if ("urn:books".equals(namespaceURI)) {
                return "b";
            }
            if ("urn:authors".equals(namespaceURI)) {
                return "a";
            }
            return null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            String prefix = getPrefix(namespaceURI);
            if (prefix == null) {
                return Collections.emptyIterator();
            }
            return Collections.singleton(prefix).iterator();
        }
    }

    private static final class FixedLocation implements Location {
        private final int lineNumber;
        private final int columnNumber;
        private final int characterOffset;
        private final String systemId;
        private final String publicId;

        private FixedLocation(int lineNumber, int columnNumber, int characterOffset, String systemId, String publicId) {
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
            this.characterOffset = characterOffset;
            this.systemId = systemId;
            this.publicId = publicId;
        }

        @Override
        public int getLineNumber() {
            return lineNumber;
        }

        @Override
        public int getColumnNumber() {
            return columnNumber;
        }

        @Override
        public int getCharacterOffset() {
            return characterOffset;
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public String getSystemId() {
            return systemId;
        }
    }
}

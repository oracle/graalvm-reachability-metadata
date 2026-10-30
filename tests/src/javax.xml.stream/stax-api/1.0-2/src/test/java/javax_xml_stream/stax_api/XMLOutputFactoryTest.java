/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_stream.stax_api;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
public class XMLOutputFactoryTest {
    @Test
    void writesNamespacedDocumentAndEscapesTextAndAttributes() throws Exception {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        factory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, false);
        StringWriter output = new StringWriter();
        XMLStreamWriter writer = factory.createXMLStreamWriter(output);
        try {
            writer.writeStartDocument();
            writer.writeStartElement("c", "catalog", "urn:catalog");
            writer.writeNamespace("c", "urn:catalog");
            writer.writeAttribute("label", "Books & \"guides\"");
            writer.writeStartElement("c", "item", "urn:catalog");
            writer.writeCharacters("A < B & C");
            writer.writeEndElement();
            writer.writeEmptyElement("c", "available", "urn:catalog");
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
        } finally {
            writer.close();
        }

        assertTrue(output.toString().contains("&lt;"));
        assertTrue(output.toString().contains("&amp;"));
        XMLStreamReader reader = XMLInputFactory.newInstance()
                .createXMLStreamReader(new StringReader(output.toString()));
        try {
            assertEquals(XMLStreamConstants.START_ELEMENT, reader.nextTag());
            assertEquals(new QName("urn:catalog", "catalog"), reader.getName());
            assertEquals("Books & \"guides\"", reader.getAttributeValue(null, "label"));
            assertEquals(XMLStreamConstants.START_ELEMENT, reader.nextTag());
            assertEquals(new QName("urn:catalog", "item"), reader.getName());
            assertEquals("A < B & C", reader.getElementText());
            assertEquals(XMLStreamConstants.START_ELEMENT, reader.nextTag());
            assertEquals(new QName("urn:catalog", "available"), reader.getName());
            assertEquals(XMLStreamConstants.END_ELEMENT, reader.nextTag());
            assertEquals(XMLStreamConstants.END_ELEMENT, reader.nextTag());
            assertEquals(XMLStreamConstants.END_DOCUMENT, reader.next());
        } finally {
            reader.close();
        }
    }

    @Test
    void copiesAnEventReaderToAnEventWriter() throws Exception {
        String xml = """
                <message priority="high">Hello &amp; welcome</message>
                """;
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        XMLEventReader source = inputFactory.createXMLEventReader(new StringReader(xml));
        StringWriter output = new StringWriter();
        try {
            XMLEventWriter writer = XMLOutputFactory.newInstance().createXMLEventWriter(output);
            try {
                writer.add(source);
                writer.flush();
            } finally {
                writer.close();
            }
        } finally {
            source.close();
        }

        XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(output.toString()));
        try {
            assertEquals(XMLStreamConstants.START_ELEMENT, reader.nextTag());
            assertEquals("message", reader.getLocalName());
            assertEquals("high", reader.getAttributeValue(null, "priority"));
            assertEquals("Hello & welcome", reader.getElementText());
            while (reader.hasNext()) {
                reader.next();
            }
            assertEquals(XMLStreamConstants.END_DOCUMENT, reader.getEventType());
        } finally {
            reader.close();
        }
    }
}

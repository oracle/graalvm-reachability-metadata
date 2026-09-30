/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_stream_buffer.streambuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sun.xml.stream.buffer.MutableXMLStreamBuffer;
import com.sun.xml.stream.buffer.XMLStreamBuffer;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.junit.jupiter.api.Test;
import org.xml.sax.XMLReader;

public class StreambufferTest {
    private static final String XML = "<root xmlns='urn:test' id='7'><item>payload</item></root>";

    @Test
    void createsBufferFromStaxAndReadsItBack() throws Exception {
        XMLStreamReader input = XMLInputFactory.newFactory()
                .createXMLStreamReader(new StringReader(XML));
        XMLStreamBuffer buffer = XMLStreamBuffer.createNewBufferFromXMLStreamReader(input);

        XMLStreamReader output = buffer.readAsXMLStreamReader();
        assertEquals(XMLStreamConstants.START_ELEMENT, output.next());
        assertEquals("root", output.getLocalName());
        assertEquals("7", output.getAttributeValue(null, "id"));
        assertEquals(XMLStreamConstants.START_ELEMENT, output.next());
        assertEquals("item", output.getLocalName());
        assertEquals(XMLStreamConstants.CHARACTERS, output.next());
        assertEquals("payload", output.getText());
        assertEquals(XMLStreamConstants.END_ELEMENT, output.next());
        assertEquals(XMLStreamConstants.END_ELEMENT, output.next());
        assertEquals(XMLStreamConstants.END_DOCUMENT, output.next());
    }

    @Test
    void createsBufferThroughStaxWriter() throws Exception {
        MutableXMLStreamBuffer buffer = new MutableXMLStreamBuffer();
        XMLStreamWriter writer = buffer.createFromXMLStreamWriter();

        writer.writeStartElement("root");
        writer.writeAttribute("id", "7");
        writer.writeStartElement("item");
        writer.writeCharacters("payload");
        writer.writeEndElement();
        writer.writeEndElement();
        writer.close();

        XMLStreamReader output = buffer.readAsXMLStreamReader();
        assertEquals(XMLStreamConstants.START_ELEMENT, output.next());
        assertEquals("root", output.getLocalName());
        assertEquals("7", output.getAttributeValue(null, "id"));
        assertEquals(XMLStreamConstants.START_ELEMENT, output.next());
        assertEquals("item", output.getLocalName());
        assertEquals("payload", output.getElementText());
    }

    @Test
    void createsBufferFromSaxAndReadsItBack() throws Exception {
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        XMLReader input = parserFactory.newSAXParser().getXMLReader();
        XMLStreamBuffer buffer;
        try (ByteArrayInputStream source =
                new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8))) {
            buffer = XMLStreamBuffer.createNewBufferFromXMLReader(input, source);
        }

        XMLStreamReader output = buffer.readAsXMLStreamReader();
        assertEquals(XMLStreamConstants.START_ELEMENT, output.next());
        assertEquals("root", output.getLocalName());
        assertEquals("7", output.getAttributeValue(null, "id"));
        assertEquals(XMLStreamConstants.START_ELEMENT, output.next());
        assertEquals("item", output.getLocalName());
        assertEquals("payload", output.getElementText());
    }

    @Test
    void writesBufferThroughStaxWriter() throws Exception {
        XMLStreamReader input = XMLInputFactory.newFactory()
                .createXMLStreamReader(new StringReader(XML));
        XMLStreamBuffer buffer = XMLStreamBuffer.createNewBufferFromXMLStreamReader(input);
        StringWriter serialized = new StringWriter();
        XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(serialized);

        buffer.writeToXMLStreamWriter(writer);
        writer.close();

        XMLStreamReader output = XMLInputFactory.newFactory()
                .createXMLStreamReader(new StringReader(serialized.toString()));
        assertEquals(XMLStreamConstants.START_DOCUMENT, output.getEventType());
        output.nextTag();
        assertEquals("root", output.getLocalName());
        output.nextTag();
        assertEquals("item", output.getLocalName());
        assertEquals("payload", output.getElementText());
    }
}

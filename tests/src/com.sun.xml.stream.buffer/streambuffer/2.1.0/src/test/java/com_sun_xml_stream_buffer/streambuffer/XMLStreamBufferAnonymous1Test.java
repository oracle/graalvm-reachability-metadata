/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_stream_buffer.streambuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.sun.xml.stream.buffer.MutableXMLStreamBuffer;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLStreamWriter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class XMLStreamBufferAnonymous1Test {
    @Test
    void writesBufferIntoDomNode() throws Exception {
        MutableXMLStreamBuffer buffer = new MutableXMLStreamBuffer();
        XMLStreamWriter writer = buffer.createFromXMLStreamWriter();
        writer.writeStartElement("message");
        writer.writeAttribute("id", "7");
        writer.writeStartElement("body");
        writer.writeCharacters("payload");
        writer.writeEndElement();
        writer.writeEndElement();
        writer.close();

        Document document = DocumentBuilderFactory.newDefaultInstance()
                .newDocumentBuilder()
                .newDocument();
        Element container = document.createElement("container");
        document.appendChild(container);

        Node appended = buffer.writeTo(container);
        Element appendedElement = (Element) appended;

        assertSame(appended, container.getLastChild());
        assertEquals("message", appendedElement.getNodeName());
        assertEquals("7", appendedElement.getAttribute("id"));
        assertEquals("payload", appendedElement.getTextContent());
    }
}

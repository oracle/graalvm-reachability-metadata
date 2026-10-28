/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import com.ctc.wstx.stax.WstxInputFactory;
import com.ctc.wstx.stax.WstxOutputFactory;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class Woodstox_coreTest {
    @Test
    void roundTripsTextAndAttributes() throws Exception {
        StringWriter output = new StringWriter();
        XMLStreamWriter writer = new WstxOutputFactory().createXMLStreamWriter(output);
        try {
            writer.writeStartDocument();
            writer.writeStartElement("message");
            writer.writeAttribute("category", "news & updates");
            writer.writeCharacters("Read <this> & enjoy");
            writer.writeEndElement();
            writer.writeEndDocument();
        } finally {
            writer.close();
        }

        XMLStreamReader reader = new WstxInputFactory()
                .createXMLStreamReader(new StringReader(output.toString()));
        try {
            assertEquals(XMLStreamConstants.START_ELEMENT, reader.nextTag());
            assertEquals("message", reader.getLocalName());
            assertEquals("news & updates", reader.getAttributeValue(null, "category"));
            assertEquals("Read <this> & enjoy", reader.getElementText());
            assertEquals(XMLStreamConstants.END_ELEMENT, reader.getEventType());
            assertEquals(XMLStreamConstants.END_DOCUMENT, reader.next());
            assertFalse(reader.hasNext());
        } finally {
            reader.close();
        }
    }
}

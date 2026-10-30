/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_woodstox.woodstox_core_asl;

import com.ctc.wstx.stax.WstxInputFactory;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class Woodstox_core_aslTest {
    @Test
    void readsNamespacedElementWithAttributeAndEscapedText() throws XMLStreamException {
        WstxInputFactory factory = new WstxInputFactory();
        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader("""
                <catalog xmlns="urn:catalog"><item id="42">Tea &amp; biscuits</item></catalog>
                """));
        try {
            assertEquals(XMLStreamConstants.START_ELEMENT, reader.nextTag());
            assertEquals("catalog", reader.getLocalName());
            assertEquals("urn:catalog", reader.getNamespaceURI());

            assertEquals(XMLStreamConstants.START_ELEMENT, reader.nextTag());
            assertEquals("item", reader.getLocalName());
            assertEquals("urn:catalog", reader.getNamespaceURI());
            assertEquals("42", reader.getAttributeValue(null, "id"));
            assertEquals("Tea & biscuits", reader.getElementText());
            assertEquals(XMLStreamConstants.END_ELEMENT, reader.getEventType());
            assertEquals("item", reader.getLocalName());

            assertEquals(XMLStreamConstants.END_ELEMENT, reader.nextTag());
            assertEquals("catalog", reader.getLocalName());
            while (reader.hasNext()) {
                reader.next();
            }
            assertEquals(XMLStreamConstants.END_DOCUMENT, reader.getEventType());
            assertFalse(reader.hasNext());
        } finally {
            reader.close();
        }
    }
}

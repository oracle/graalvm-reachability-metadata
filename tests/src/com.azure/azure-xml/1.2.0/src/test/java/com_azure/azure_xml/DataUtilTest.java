/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_xml;

import com.azure.xml.XmlReader;
import com.azure.xml.XmlToken;
import javax.xml.stream.XMLStreamException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataUtilTest {
    private static final int NAMESPACE_COUNT = 17;

    @Test
    void readsElementsFromMoreThanSixteenNamespaceBindings() throws XMLStreamException {
        String xml = createNamespacedDocument();

        try (XmlReader reader = XmlReader.fromString(xml)) {
            assertEquals(XmlToken.START_DOCUMENT, reader.currentToken());
            assertEquals(XmlToken.START_ELEMENT, reader.nextElement());
            assertEquals("root", reader.getElementLocalName());

            for (int i = 0; i < NAMESPACE_COUNT; i++) {
                assertEquals(XmlToken.START_ELEMENT, reader.nextElement());
                assertEquals("urn:namespace:" + i, reader.getElementNamespaceUri());
                assertEquals("value", reader.getElementLocalName());
                assertEquals("content-" + i, reader.getStringElement());
            }

            assertEquals(XmlToken.END_ELEMENT, reader.nextElement());
            assertEquals("root", reader.getElementLocalName());
            assertEquals(XmlToken.END_DOCUMENT, reader.nextElement());
        }
    }

    private static String createNamespacedDocument() {
        StringBuilder xml = new StringBuilder("<root");
        for (int i = 0; i < NAMESPACE_COUNT; i++) {
            xml.append(" xmlns:n").append(i).append("=\"urn:namespace:").append(i).append("\"");
        }
        xml.append('>');
        for (int i = 0; i < NAMESPACE_COUNT; i++) {
            xml.append("<n").append(i).append(":value>content-").append(i).append("</n").append(i)
                .append(":value>");
        }
        return xml.append("</root>").toString();
    }
}

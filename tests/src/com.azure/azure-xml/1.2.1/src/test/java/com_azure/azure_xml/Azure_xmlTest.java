/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_xml;

import com.azure.xml.XmlReader;
import com.azure.xml.XmlSerializable;
import com.azure.xml.XmlToken;
import com.azure.xml.XmlWriter;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import javax.xml.stream.XMLStreamException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(60)
public class Azure_xmlTest {
    private static final int NAMESPACE_COUNT = 17;

    @Test
    void roundTripsSerializableModelWithTypedValues() throws XMLStreamException {
        Inventory expected = new Inventory("storage & compute", true, 19.75D, 2.5F, 42, 9_000_000_000L,
            new byte[] { 0, 1, 2, 127 }, new BigDecimal("1234.50"), "left & <right>");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (XmlWriter writer = XmlWriter.toStream(output)) {
            writer.writeStartDocument().writeXml(expected, "inventory");
        }

        Inventory actual;
        try (XmlReader reader = XmlReader.fromBytes(output.toByteArray())) {
            actual = Inventory.fromXml(reader, "inventory");
            assertEquals(XmlToken.END_ELEMENT, reader.currentToken());
            assertEquals(XmlToken.END_DOCUMENT, reader.nextElement());
        }

        assertEquals(expected.name, actual.name);
        assertEquals(expected.active, actual.active);
        assertEquals(expected.price, actual.price);
        assertEquals(expected.weight, actual.weight);
        assertEquals(expected.quantity, actual.quantity);
        assertEquals(expected.sequence, actual.sequence);
        assertArrayEquals(expected.checksum, actual.checksum);
        assertEquals(expected.balance, actual.balance);
        assertEquals(expected.description, actual.description);
        assertTrue(actual.markerSeen);
        assertNull(actual.omitted);
    }

    @Test
    void writesNamespaceQualifiedXmlToCharacterWriter() throws XMLStreamException {
        String namespace = "urn:catalog";
        StringWriter output = new StringWriter();

        try (XmlWriter writer = XmlWriter.toWriter(output)) {
            writer.writeStartDocument()
                .writeStartElement("catalog")
                .writeNamespace(namespace)
                .writeStringElement(namespace, "name", "storage & compute")
                .writeEndElement();
        }

        try (XmlReader reader = XmlReader.fromString(output.toString())) {
            assertEquals(XmlToken.START_ELEMENT, reader.nextElement());
            assertEquals(namespace, reader.getElementNamespaceUri());
            assertEquals("catalog", reader.getElementLocalName());
            assertEquals(XmlToken.START_ELEMENT, reader.nextElement());
            assertTrue(reader.elementNameMatches(namespace, "name"));
            assertEquals("storage & compute", reader.getStringElement());
            assertEquals(XmlToken.END_ELEMENT, reader.nextElement());
            assertEquals("catalog", reader.getElementLocalName());
            assertEquals(XmlToken.END_DOCUMENT, reader.nextElement());
        }
    }

    @Test
    void readsAndWritesCallerProvidedCharacterStreams() throws XMLStreamException {
        StringWriter output = new StringWriter();

        try (XmlWriter writer = XmlWriter.toWriter(output)) {
            writer.writeStartDocument()
                .writeStartElement("metrics")
                .writeBooleanElement("enabled", true)
                .writeIntElement("count", 7)
                .writeEndElement();
        }

        try (XmlReader reader = XmlReader.fromReader(new StringReader(output.toString()))) {
            assertEquals(XmlToken.START_DOCUMENT, reader.currentToken());
            assertEquals(XmlToken.START_ELEMENT, reader.nextElement());
            assertTrue(reader.elementNameMatches("metrics"));
            assertEquals(XmlToken.START_ELEMENT, reader.nextElement());
            assertTrue(reader.getBooleanElement());
            assertEquals(XmlToken.START_ELEMENT, reader.nextElement());
            assertEquals(7, reader.getIntElement());
            assertEquals(XmlToken.END_ELEMENT, reader.nextElement());
            assertEquals(XmlToken.END_DOCUMENT, reader.nextElement());
        }
    }

    @Test
    void readsElementsWithMoreThanSixteenNamespaceBindings() throws XMLStreamException {
        String xml = createNamespacedDocument();

        try (XmlReader reader = XmlReader.fromString(xml)) {
            assertEquals(XmlToken.START_DOCUMENT, reader.currentToken());
            assertEquals(XmlToken.START_ELEMENT, reader.nextElement());
            assertEquals("root", reader.getElementLocalName());

            for (int i = 0; i < NAMESPACE_COUNT; i++) {
                assertEquals(XmlToken.START_ELEMENT, reader.nextElement());
                assertTrue(reader.elementNameMatches("urn:namespace:" + i, "value"));
                assertEquals("urn:namespace:" + i, reader.getElementName().getNamespaceURI());
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

    private static final class Inventory implements XmlSerializable<Inventory> {
        private String name;
        private boolean active;
        private double price;
        private float weight;
        private int quantity;
        private long sequence;
        private byte[] checksum;
        private BigDecimal balance;
        private String description;
        private boolean markerSeen;
        private String omitted;

        private Inventory() {
        }

        private Inventory(String name, boolean active, double price, float weight, int quantity, long sequence,
            byte[] checksum, BigDecimal balance, String description) {
            this.name = name;
            this.active = active;
            this.price = price;
            this.weight = weight;
            this.quantity = quantity;
            this.sequence = sequence;
            this.checksum = checksum;
            this.balance = balance;
            this.description = description;
        }

        private static Inventory fromXml(XmlReader reader, String rootElementName) throws XMLStreamException {
            return reader.readObject(rootElementName, xml -> {
                Inventory inventory = new Inventory();
                inventory.name = xml.getStringAttribute(null, "name");
                inventory.active = xml.getBooleanAttribute(null, "active");
                inventory.price = xml.getDoubleAttribute(null, "price");
                inventory.weight = xml.getFloatAttribute(null, "weight");
                inventory.quantity = xml.getIntAttribute(null, "quantity");
                inventory.sequence = xml.getLongAttribute(null, "sequence");
                inventory.checksum = xml.getBinaryAttribute(null, "checksum");
                inventory.omitted = xml.getNullableAttribute(null, "omitted", value -> value);

                while (xml.nextElement() != XmlToken.END_ELEMENT) {
                    xml.processNextElement((namespaceUri, localName, element) -> {
                        assertEquals("", namespaceUri);
                        switch (localName) {
                            case "balance":
                                inventory.balance = element.getNullableElement(BigDecimal::new);
                                break;
                            case "description":
                                inventory.description = element.getStringElement();
                                break;
                            case "marker":
                                inventory.markerSeen = true;
                                assertNull(element.getStringElement());
                                break;
                            default:
                                element.skipElement();
                                break;
                        }
                    });
                }
                return inventory;
            });
        }

        @Override
        public XmlWriter toXml(XmlWriter writer, String rootElementName) throws XMLStreamException {
            int cdataStart = description.indexOf('<');
            String plainText = description.substring(0, cdataStart);
            String cdataText = description.substring(cdataStart);

            writer.writeStartElement(rootElementName)
                .writeStringAttribute("name", name)
                .writeBooleanAttribute("active", active)
                .writeDoubleAttribute("price", price)
                .writeFloatAttribute("weight", weight)
                .writeIntAttribute("quantity", quantity)
                .writeLongAttribute("sequence", sequence)
                .writeBinaryAttribute("checksum", checksum)
                .writeStringAttribute("omitted", null)
                .writeNumberElement("balance", balance)
                .writeStartElement("description")
                .writeString(plainText)
                .writeCDataString(cdataText)
                .writeEndElement()
                .writeStartSelfClosingElement("marker")
                .writeStartElement("extension")
                .writeStringElement("ignored", "future value")
                .writeEndElement()
                .writeEndElement();
            return writer;
        }
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_santuario.xmlsec;

import org.apache.xml.security.stax.ext.SecurePart;
import org.apache.xml.security.stax.ext.XMLSec;
import org.apache.xml.security.stax.ext.XMLSecurityConstants;
import org.apache.xml.security.stax.ext.XMLSecurityProperties;
import org.apache.xml.security.stax.securityToken.SecurityTokenConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises StAX XML Encryption for customer contact details in a routed fulfillment order.
@Timeout(60)
public class CustomerOrderStreamingEncryptionTest {
    private static final String NAMESPACE = "urn:example:fulfillment";
    private static final QName CONTACT = new QName(NAMESPACE, "Contact");
    private static final QName ENCRYPTED_DATA = new QName("http://www.w3.org/2001/04/xmlenc#", "EncryptedData");

    @Test
    public void routesOrderWithoutDisclosingContactAndDecryptsAtFulfillment() throws Exception {
        XMLSec.init();
        // The order service and fulfillment service provision the same symmetric key out of band.
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey fulfillmentKey = generator.generateKey();
        byte[] transmittedOrder = encryptOrder(fulfillmentKey);

        String wireXml = new String(transmittedOrder, StandardCharsets.UTF_8);
        assertFalse(wireXml.contains("alex@example.test"));
        assertFalse(wireXml.contains("24 Market Street"));
        try (ByteArrayInputStream input = new ByteArrayInputStream(transmittedOrder)) {
            XMLStreamReader reader = inputFactory().createXMLStreamReader(input);
            try {
                Order routed = readOrder(reader);
                assertEquals("ORDER-704", routed.number());
                assertEquals("warehouse-west", routed.route());
                assertNull(routed.email());
                assertNull(routed.address());
                assertEquals(1, routed.encryptedParts());
            } finally {
                reader.close();
            }
        }

        Order fulfilled = decryptOrder(transmittedOrder, fulfillmentKey);
        assertEquals(new Order("ORDER-704", "warehouse-west", "alex@example.test", "24 Market Street", 0),
                fulfilled);

        SecretKey unrelatedKey = generator.generateKey();
        assertThrows(XMLStreamException.class, () -> decryptOrder(transmittedOrder, unrelatedKey),
                "A service without the provisioned key must not receive customer contact details");
    }

    private static byte[] encryptOrder(SecretKey key) throws Exception {
        XMLSecurityProperties properties = new XMLSecurityProperties();
        properties.addAction(XMLSecurityConstants.ENCRYPTION);
        properties.setEncryptionKey(key);
        properties.setEncryptionSymAlgorithm(XMLSecurityConstants.NS_XENC11_AES128_GCM);
        properties.setEncryptionKeyIdentifier(SecurityTokenConstants.KeyIdentifier_KeyName);
        properties.setEncryptionKeyName("fulfillment-contact");
        properties.addEncryptionPart(new SecurePart(CONTACT, SecurePart.Modifier.Element));

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XMLStreamWriter writer = XMLSec.getOutboundXMLSec(properties)
                    .processOutMessage(output, StandardCharsets.UTF_8.name());
            try {
                writer.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
                writer.writeStartElement("f", "Order", NAMESPACE);
                writer.writeNamespace("f", NAMESPACE);
                writer.writeAttribute("number", "ORDER-704");
                writeField(writer, "Route", "warehouse-west");
                writer.writeStartElement("f", "Contact", NAMESPACE);
                writeField(writer, "Email", "alex@example.test");
                writeField(writer, "Address", "24 Market Street");
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeEndDocument();
            } finally {
                writer.close();
            }
            return output.toByteArray();
        }
    }

    private static void writeField(XMLStreamWriter writer, String name, String value) throws XMLStreamException {
        writer.writeStartElement("f", name, NAMESPACE);
        writer.writeCharacters(value);
        writer.writeEndElement();
    }

    private static Order decryptOrder(byte[] order, SecretKey key) throws Exception {
        XMLSecurityProperties properties = new XMLSecurityProperties();
        properties.setDecryptionKey(key);
        try (ByteArrayInputStream input = new ByteArrayInputStream(order)) {
            XMLStreamReader reader = XMLSec.getInboundWSSec(properties)
                    .processInMessage(inputFactory().createXMLStreamReader(input));
            try {
                // Consume the complete stream before returning any decrypted customer data.
                return readOrder(reader);
            } finally {
                reader.close();
            }
        }
    }

    private static XMLInputFactory inputFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    private static Order readOrder(XMLStreamReader reader) throws XMLStreamException {
        String number = null;
        String route = null;
        String email = null;
        String address = null;
        int encryptedParts = 0;
        while (reader.hasNext()) {
            if (reader.next() != XMLStreamReader.START_ELEMENT) {
                continue;
            }
            QName name = reader.getName();
            if (new QName(NAMESPACE, "Order").equals(name)) {
                number = reader.getAttributeValue(null, "number");
            } else if (new QName(NAMESPACE, "Route").equals(name)) {
                route = reader.getElementText();
            } else if (new QName(NAMESPACE, "Email").equals(name)) {
                email = reader.getElementText();
            } else if (new QName(NAMESPACE, "Address").equals(name)) {
                address = reader.getElementText();
            } else if (ENCRYPTED_DATA.equals(name)) {
                encryptedParts++;
            }
        }
        return new Order(number, route, email, address, encryptedParts);
    }

    private record Order(String number, String route, String email, String address, int encryptedParts) {
    }
}

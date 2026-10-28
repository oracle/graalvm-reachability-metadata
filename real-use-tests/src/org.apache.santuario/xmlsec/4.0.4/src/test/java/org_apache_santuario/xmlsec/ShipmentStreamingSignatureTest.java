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
import org.apache.xml.security.stax.securityEvent.SignedElementSecurityEvent;
import org.apache.xml.security.stax.securityToken.SecurityTokenConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises StAX streaming XML signatures for authenticated carrier shipment notices.
@Timeout(60)
public class ShipmentStreamingSignatureTest {
    private static final String NAMESPACE = "urn:example:shipping";
    private static final QName NOTICE = new QName(NAMESPACE, "Notice");
    private static final QName SHIPMENT = new QName(NAMESPACE, "Shipment");

    @Test
    public void acceptsVerifiedShipmentAndRejectsTamperedOrUnsignedNotices() throws Exception {
        XMLSec.init();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair carrierKeys = generator.generateKeyPair();

        byte[] signedNotice = signNotice(carrierKeys);
        Shipment received = receiveNotice(signedNotice, carrierKeys.getPublic());
        assertEquals(new Shipment("SHIP-17", "TRACK-7829", "12"), received);

        String altered = new String(signedNotice, StandardCharsets.UTF_8)
                .replace("TRACK-7829", "TRACK-0000");
        assertThrows(XMLStreamException.class, () -> receiveNotice(
                altered.getBytes(StandardCharsets.UTF_8), carrierKeys.getPublic()),
                "Changing a tracking number must invalidate the carrier's signature");

        byte[] unsigned = """
                <s:Notice xmlns:s="urn:example:shipping">
                    <s:Shipment number="SHIP-17">
                        <s:Tracking>TRACK-7829</s:Tracking><s:Packages>12</s:Packages>
                    </s:Shipment>
                </s:Notice>
                """.getBytes(StandardCharsets.UTF_8);
        assertThrows(XMLStreamException.class, () -> receiveNotice(unsigned, carrierKeys.getPublic()),
                "A well-formed notice without a signed shipment must not enter fulfillment");
    }

    private static byte[] signNotice(KeyPair carrierKeys) throws Exception {
        XMLSecurityProperties properties = new XMLSecurityProperties();
        properties.addAction(XMLSecurityConstants.SIGNATURE);
        properties.setSignatureKey(carrierKeys.getPrivate());
        properties.setSignatureVerificationKey(carrierKeys.getPublic());
        properties.setSignatureKeyIdentifier(SecurityTokenConstants.KeyIdentifier_KeyName);
        properties.setSignatureKeyName("carrier-main");
        properties.setSignatureAlgorithm(XMLSecurityConstants.NS_XMLDSIG_RSASHA256);
        properties.setSignatureDigestAlgorithm(XMLSecurityConstants.NS_XENC_SHA256);
        properties.setSignatureCanonicalizationAlgorithm(XMLSecurityConstants.NS_C14N_EXCL_OMIT_COMMENTS);
        properties.addSignaturePart(new SecurePart(SHIPMENT, SecurePart.Modifier.Element));

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XMLStreamWriter writer = XMLSec.getOutboundXMLSec(properties)
                    .processOutMessage(output, StandardCharsets.UTF_8.name());
            try {
                writer.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
                writer.writeStartElement("s", "Notice", NAMESPACE);
                writer.writeNamespace("s", NAMESPACE);
                writer.writeStartElement("s", "Shipment", NAMESPACE);
                writer.writeAttribute("number", "SHIP-17");
                writer.writeStartElement("s", "Tracking", NAMESPACE);
                writer.writeCharacters("TRACK-7829");
                writer.writeEndElement();
                writer.writeStartElement("s", "Packages", NAMESPACE);
                writer.writeCharacters("12");
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeEndDocument();
            } finally {
                writer.close();
            }
            return output.toByteArray();
        }
    }

    private static Shipment receiveNotice(byte[] notice, PublicKey trustedCarrierKey) throws Exception {
        XMLSecurityProperties properties = new XMLSecurityProperties();
        // Trust comes from the carrier's provisioned key, not the message's KeyInfo.
        properties.setSignatureVerificationKey(trustedCarrierKey);
        List<SignedElementSecurityEvent> signedElements = new ArrayList<>();
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        String number = null;
        String tracking = null;
        String packages = null;
        int shipmentCount = 0;
        int depth = 0;
        boolean insideShipment = false;
        try (ByteArrayInputStream input = new ByteArrayInputStream(notice)) {
            XMLStreamReader reader = XMLSec.getInboundWSSec(properties).processInMessage(
                    factory.createXMLStreamReader(input), null, event -> {
                        if (event instanceof SignedElementSecurityEvent signed) {
                            signedElements.add(signed);
                        }
                    });
            try {
                // Verification is lazy: do not release any shipment until the entire stream is consumed.
                while (reader.hasNext()) {
                    int eventType = reader.next();
                    if (eventType == XMLStreamReader.START_ELEMENT) {
                        depth++;
                        if (SHIPMENT.equals(reader.getName())) {
                            shipmentCount++;
                            insideShipment = depth == 2;
                            number = reader.getAttributeValue(null, "number");
                        } else if (insideShipment && depth == 3
                                && new QName(NAMESPACE, "Tracking").equals(reader.getName())) {
                            tracking = reader.getElementText();
                            depth--;
                        } else if (insideShipment && depth == 3
                                && new QName(NAMESPACE, "Packages").equals(reader.getName())) {
                            packages = reader.getElementText();
                            depth--;
                        }
                    } else if (eventType == XMLStreamReader.END_ELEMENT) {
                        if (SHIPMENT.equals(reader.getName())) {
                            insideShipment = false;
                        }
                        depth--;
                    }
                }
            } finally {
                reader.close();
            }
        }
        if (shipmentCount != 1 || number == null || tracking == null || packages == null
                || signedElements.stream().noneMatch(event -> event.isSigned()
                && event.getElementPath().equals(List.of(NOTICE, SHIPMENT)))) {
            throw new SecurityException("Expected one authenticated shipment directly inside the notice");
        }
        return new Shipment(number, tracking, packages);
    }

    private record Shipment(String number, String tracking, String packages) {
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_santuario.xmlsec;

import org.apache.xml.security.Init;
import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.apache.xml.security.utils.XMLUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises DOM enveloped XML signatures for purchase-order integrity across transport.
@Timeout(60)
public class PurchaseOrderEnvelopedSignatureTest {
    private static final String ORDER_NAMESPACE = "urn:example:purchase-order";

    @Test
    public void verifiesReceivedOrderAndRejectsChangedTotal() throws Exception {
        Init.init();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair supplierKeys = generator.generateKeyPair();

        Document order = parse("""
                <po:Order xmlns:po="urn:example:purchase-order" number="PO-2048">
                    <po:Buyer>Northwind Books</po:Buyer>
                    <po:Item sku="BOOK-17" quantity="3"/>
                    <po:Total currency="USD">89.70</po:Total>
                </po:Order>
                """.getBytes(StandardCharsets.UTF_8));

        XMLSignature signature = new XMLSignature(order, "",
                XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        order.getDocumentElement().appendChild(signature.getElement());
        Transforms transforms = new Transforms(order);
        transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
        transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        signature.addDocument("", transforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);
        signature.sign(supplierKeys.getPrivate());

        byte[] transmittedOrder;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XMLUtils.outputDOM(order, output);
            transmittedOrder = output.toByteArray();
        }

        Document received = parse(transmittedOrder);
        // The receiver trusts the supplier's provisioned key, not a key supplied in the XML.
        assertTrue(readSignature(received).checkSignatureValue(supplierKeys.getPublic()));
        assertEquals("PO-2048", received.getDocumentElement().getAttribute("number"));
        assertEquals("Northwind Books",
                received.getElementsByTagNameNS(ORDER_NAMESPACE, "Buyer").item(0).getTextContent());
        assertEquals("89.70",
                received.getElementsByTagNameNS(ORDER_NAMESPACE, "Total").item(0).getTextContent());

        Document altered = parse(transmittedOrder);
        altered.getElementsByTagNameNS(ORDER_NAMESPACE, "Total").item(0).setTextContent("9.70");
        assertFalse(readSignature(altered).checkSignatureValue(supplierKeys.getPublic()),
                "An order with a changed total must not be accepted");
    }

    private static Document parse(byte[] xml) throws Exception {
        try (ByteArrayInputStream input = new ByteArrayInputStream(xml)) {
            return XMLUtils.read(input, true);
        }
    }

    private static XMLSignature readSignature(Document document) throws Exception {
        assertEquals(1, document.getElementsByTagNameNS(Constants.SignatureSpecNS, "Signature").getLength());
        Element element = (Element) document.getElementsByTagNameNS(Constants.SignatureSpecNS, "Signature").item(0);
        return new XMLSignature(element, "", true);
    }
}

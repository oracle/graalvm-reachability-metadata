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
import org.apache.xml.security.transforms.params.XPath2FilterContainer;
import org.apache.xml.security.utils.Constants;
import org.apache.xml.security.utils.XMLUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises XPath Filter 2.0 transforms for signed invoices with editable, unauthenticated routing notes.
@Timeout(60)
public class InvoiceRoutingXPathFilterTest {
    private static final String NAMESPACE = "urn:example:invoice";
    private static final String ROUTING_XPATH = "/i:Delivery/i:Invoice/i:RoutingNote";

    @Test
    public void preservesInvoiceAuthenticationAcrossRoutingEditsButRejectsAmountChanges() throws Exception {
        Init.init();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair supplier = generator.generateKeyPair();
        byte[] transmitted = signInvoice(supplier);
        Invoice expected = new Invoice("INV-704", "Northwind Books", "1250.00", "EUR");
        assertEquals(expected, receiveInvoice(transmitted, supplier.getPublic()));

        // A gateway updates operational notes without access to the supplier's signing key.
        Document routed = parse(transmitted);
        singleElement(routed, NAMESPACE, "RoutingNote").setTextContent("Forward to accounts-payable-west");
        byte[] forwarded = serialize(routed);
        assertEquals("Forward to accounts-payable-west",
                singleElement(parse(forwarded), NAMESPACE, "RoutingNote").getTextContent());
        assertEquals(expected, receiveInvoice(forwarded, supplier.getPublic()),
                "Changing only the excluded routing note must preserve invoice authentication");

        Document altered = parse(forwarded);
        singleElement(altered, NAMESPACE, "Amount").setTextContent("9250.00");
        byte[] alteredDelivery = serialize(altered);
        assertThrows(SecurityException.class, () -> receiveInvoice(alteredDelivery, supplier.getPublic()),
                "A routing gateway must not be able to change the authenticated invoice amount");
    }

    private static byte[] signInvoice(KeyPair supplier) throws Exception {
        Document document = parse("""
                <i:Delivery xmlns:i="urn:example:invoice">
                    <i:Invoice Id="invoice" number="INV-704">
                        <i:Buyer>Northwind Books</i:Buyer>
                        <i:Amount currency="EUR">1250.00</i:Amount>
                        <i:RoutingNote>Received at central gateway</i:RoutingNote>
                    </i:Invoice>
                </i:Delivery>
                """.getBytes(StandardCharsets.UTF_8));
        singleElement(document, NAMESPACE, "Invoice").setIdAttributeNS(null, "Id", true);
        XMLSignature signature = new XMLSignature(document, "",
                XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        document.getDocumentElement().appendChild(signature.getElement());

        XPath2FilterContainer routingFilter = XPath2FilterContainer.newInstanceSubtract(document, ROUTING_XPATH);
        routingFilter.setXPathNamespaceContext("i", NAMESPACE);
        Transforms transforms = new Transforms(document);
        // Filter 2.0 subtracts the complete routing-note subtree, not just its element node.
        transforms.addTransform(Transforms.TRANSFORM_XPATH2FILTER, routingFilter.getElement());
        transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        signature.addDocument("#invoice", transforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);
        signature.sign(supplier.getPrivate());
        return serialize(document);
    }

    private static Invoice receiveInvoice(byte[] delivery, PublicKey trustedSupplier) throws Exception {
        Document document = parse(delivery);
        Element root = document.getDocumentElement();
        Element invoice = singleElement(document, NAMESPACE, "Invoice");
        Element signatureElement = singleElement(document, Constants.SignatureSpecNS, "Signature");
        if (!NAMESPACE.equals(root.getNamespaceURI()) || !"Delivery".equals(root.getLocalName())
                || invoice.getParentNode() != root || signatureElement.getParentNode() != root
                || !"invoice".equals(invoice.getAttribute("Id"))) {
            throw new SecurityException("Unexpected invoice delivery structure");
        }
        invoice.setIdAttributeNS(null, "Id", true);
        XMLSignature signature = new XMLSignature(signatureElement, "", true);
        if (signature.getSignedInfo().getLength() != 1
                || !"#invoice".equals(signature.getSignedInfo().item(0).getURI())) {
            throw new SecurityException("Expected authentication of this invoice");
        }
        Transforms transforms = signature.getSignedInfo().item(0).getTransforms();
        if (transforms == null || transforms.getLength() != 2
                || !Transforms.TRANSFORM_XPATH2FILTER.equals(transforms.item(0).getURI())
                || !Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS.equals(transforms.item(1).getURI())) {
            throw new SecurityException("Unexpected invoice signature transforms");
        }
        // Enforce the agreed exclusion profile rather than trusting arbitrary sender-supplied XPath.
        Element filterElement = singleElement(document, XPath2FilterContainer.XPathFilter2NS, "XPath");
        XPath2FilterContainer filter = XPath2FilterContainer.newInstance(filterElement, "");
        if (filterElement.getParentNode() != transforms.item(0).getElement()
                || !filter.isSubtract() || !ROUTING_XPATH.equals(filter.getXPathFilterStr())
                || !NAMESPACE.equals(filterElement.lookupNamespaceURI("i"))) {
            throw new SecurityException("Only routing notes may be excluded from authentication");
        }
        if (!signature.checkSignatureValue(trustedSupplier)) {
            throw new SecurityException("Unauthenticated invoice");
        }
        Element buyer = singleElement(document, NAMESPACE, "Buyer");
        Element amount = singleElement(document, NAMESPACE, "Amount");
        if (buyer.getParentNode() != invoice || amount.getParentNode() != invoice) {
            throw new SecurityException("Business fields must be inside the authenticated invoice");
        }
        // Routing notes are intentionally not returned as authenticated business data.
        return new Invoice(invoice.getAttribute("number"), buyer.getTextContent(),
                amount.getTextContent(), amount.getAttribute("currency"));
    }

    private static Element singleElement(Document document, String namespace, String name) {
        NodeList elements = document.getElementsByTagNameNS(namespace, name);
        if (elements.getLength() != 1) {
            throw new SecurityException("Expected exactly one " + name);
        }
        return (Element) elements.item(0);
    }

    private static Document parse(byte[] xml) throws Exception {
        try (ByteArrayInputStream input = new ByteArrayInputStream(xml)) {
            return XMLUtils.read(input, true);
        }
    }

    private static byte[] serialize(Document document) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XMLUtils.outputDOM(document, output);
            return output.toByteArray();
        }
    }

    private record Invoice(String number, String buyer, String amount, String currency) {
    }
}

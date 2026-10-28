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
import org.apache.xml.security.keys.KeyInfo;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
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

/// Exercises KeyInfoReference resolution for bank advice signatures with a shared in-message key descriptor.
@Timeout(60)
public class BankAdviceKeyInfoReferenceTest {
    private static final String NAMESPACE = "urn:example:bank-advice";

    @Test
    public void resolvesReferencedBankKeyButTrustsOnlyTheProvisionedBank() throws Exception {
        Init.init();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair bankKeys = generator.generateKeyPair();
        byte[] transmitted = signAdvice(bankKeys);

        Advice accepted = receiveAdvice(transmitted, bankKeys.getPublic());
        assertEquals(new Advice("PAY-704", "1250.00", "EUR"), accepted);

        byte[] altered = new String(transmitted, StandardCharsets.UTF_8)
                .replace("1250.00", "9250.00").getBytes(StandardCharsets.UTF_8);
        assertThrows(SecurityException.class, () -> receiveAdvice(altered, bankKeys.getPublic()),
                "A resolved trusted key must still authenticate the payment amount");

        KeyPair impostorKeys = generator.generateKeyPair();
        byte[] impostorAdvice = signAdvice(impostorKeys);
        assertThrows(SecurityException.class, () -> receiveAdvice(impostorAdvice, bankKeys.getPublic()),
                "A valid signature and resolvable KeyInfo do not establish bank identity");
    }

    private static byte[] signAdvice(KeyPair bankKeys) throws Exception {
        Document document = parse("""
                <b:Delivery xmlns:b="urn:example:bank-advice">
                    <b:Advice Id="advice" payment="PAY-704">
                        <b:Amount currency="EUR">1250.00</b:Amount>
                    </b:Advice>
                </b:Delivery>
                """.getBytes(StandardCharsets.UTF_8));
        Element advice = singleElement(document, NAMESPACE, "Advice");
        advice.setIdAttributeNS(null, "Id", true);

        // A delivery can carry a shared descriptor rather than repeat the key inside each signature.
        KeyInfo bankDescriptor = new KeyInfo(document);
        bankDescriptor.setId("bank-key");
        bankDescriptor.addKeyValue(bankKeys.getPublic());
        document.getDocumentElement().appendChild(bankDescriptor.getElement());

        XMLSignature signature = new XMLSignature(document, "",
                XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        document.getDocumentElement().appendChild(signature.getElement());
        signature.getKeyInfo().addKeyInfoReference("#bank-key");
        Transforms transforms = new Transforms(document);
        transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        signature.addDocument("#advice", transforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);
        signature.sign(bankKeys.getPrivate());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XMLUtils.outputDOM(document, output);
            return output.toByteArray();
        }
    }

    private static Advice receiveAdvice(byte[] xml, PublicKey provisionedBankKey) throws Exception {
        Document document = parse(xml);
        Element delivery = document.getDocumentElement();
        Element advice = singleElement(document, NAMESPACE, "Advice");
        Element signatureElement = singleElement(document, Constants.SignatureSpecNS, "Signature");
        if (!NAMESPACE.equals(delivery.getNamespaceURI()) || !"Delivery".equals(delivery.getLocalName())
                || advice.getParentNode() != delivery || signatureElement.getParentNode() != delivery
                || !"advice".equals(advice.getAttribute("Id"))) {
            throw new SecurityException("Unexpected bank delivery structure");
        }
        advice.setIdAttributeNS(null, "Id", true);
        // Only the delivery's direct KeyInfo descriptor is an eligible reference target.
        Element descriptor = null;
        for (Element child : XMLUtils.selectDsNodes(delivery.getFirstChild(), "KeyInfo")) {
            if (descriptor != null) {
                throw new SecurityException("Ambiguous bank key descriptor");
            }
            descriptor = child;
        }
        if (descriptor == null || !"bank-key".equals(descriptor.getAttribute("Id"))) {
            throw new SecurityException("Missing bank key descriptor");
        }
        descriptor.setIdAttributeNS(null, "Id", true);

        XMLSignature signature = new XMLSignature(signatureElement, "", true);
        if (signature.getSignedInfo().getLength() != 1
                || !"#advice".equals(signature.getSignedInfo().item(0).getURI())) {
            throw new SecurityException("The signature must cover the advice being processed");
        }
        KeyInfo keyInfo = signature.getKeyInfo();
        if (keyInfo == null || keyInfo.lengthKeyInfoReference() != 1
                || !"#bank-key".equals(keyInfo.itemKeyInfoReference(0).getURI())) {
            throw new SecurityException("Expected a local bank key reference");
        }
        keyInfo.setSecureValidation(true);
        PublicKey resolvedKey = keyInfo.getPublicKey();
        // Key resolution is not trust validation: pin the resolved key before accepting business data.
        if (!provisionedBankKey.equals(resolvedKey) || !signature.checkSignatureValue(resolvedKey)) {
            throw new SecurityException("Unauthenticated bank advice");
        }
        Element amount = singleElement(document, NAMESPACE, "Amount");
        if (amount.getParentNode() != advice) {
            throw new SecurityException("Amount is outside the authenticated advice");
        }
        return new Advice(advice.getAttribute("payment"), amount.getTextContent(), amount.getAttribute("currency"));
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

    private record Advice(String payment, String amount, String currency) {
    }
}

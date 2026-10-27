/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_santuario.xmlsec;

import org.apache.jcp.xml.dsig.internal.dom.XMLDSigRI;
import org.apache.xml.security.utils.XMLUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLObject;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Santuario's JSR 105 provider for HMAC-authenticated service entitlement messages.
@Timeout(60)
public class ServiceEntitlementJsr105Test {
    private static final String NAMESPACE = "urn:example:entitlement";

    @Test
    public void provisionsOnlyFeaturesAuthenticatedByTheEntitlementService() throws Exception {
        // The entitlement and provisioning services share a key delivered by their secret manager.
        KeyGenerator generator = KeyGenerator.getInstance("HmacSHA256");
        generator.init(256);
        SecretKey serviceKey = generator.generateKey();
        byte[] message = issueEntitlement(serviceKey);

        assertEquals(new Entitlement("tenant-west", "analytics", "25"),
                receiveEntitlement(message, serviceKey));

        Document altered = parse(message);
        singleElement(altered, NAMESPACE, "Entitlement").setAttribute("seats", "250");
        byte[] escalated = serialize(altered);
        assertThrows(SecurityException.class, () -> receiveEntitlement(escalated, serviceKey),
                "A changed seat allowance must not be provisioned");

        SecretKey unrelatedServiceKey = generator.generateKey();
        assertThrows(SecurityException.class, () -> receiveEntitlement(message, unrelatedServiceKey),
                "A different service's shared key must not authenticate this entitlement");
    }

    private static byte[] issueEntitlement(SecretKey key) throws Exception {
        Document payload = parse("""
                <e:Entitlement xmlns:e="urn:example:entitlement"
                    tenant="tenant-west" feature="analytics" seats="25"/>
                """.getBytes(StandardCharsets.UTF_8));
        XMLSignatureFactory factory = signatureFactory();
        XMLObject object = factory.newXMLObject(List.of(new DOMStructure(payload.getDocumentElement())),
                "entitlement", "application/xml", null);
        Transform canonicalize = factory.newTransform(CanonicalizationMethod.EXCLUSIVE,
                (TransformParameterSpec) null);
        Reference reference = factory.newReference("#entitlement",
                factory.newDigestMethod(DigestMethod.SHA256, null), List.of(canonicalize), null, null);
        SignedInfo signedInfo = factory.newSignedInfo(
                factory.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE,
                        (C14NMethodParameterSpec) null),
                factory.newSignatureMethod(SignatureMethod.HMAC_SHA256, null), List.of(reference));

        Document envelope = payload.getImplementation().createDocument(null, null, null);
        DOMSignContext context = new DOMSignContext(key, envelope);
        context.setDefaultNamespacePrefix("ds");
        factory.newXMLSignature(signedInfo, null, List.of(object), null, null).sign(context);
        return serialize(envelope);
    }

    private static Entitlement receiveEntitlement(byte[] message, SecretKey trustedKey) throws Exception {
        Document document = parse(message);
        Element signatureElement = singleElement(document, XMLSignature.XMLNS, "Signature");
        Element objectElement = singleElement(document, XMLSignature.XMLNS, "Object");
        Element entitlement = singleElement(document, NAMESPACE, "Entitlement");
        if (signatureElement != document.getDocumentElement()
                || objectElement.getParentNode() != signatureElement
                || entitlement.getParentNode() != objectElement
                || !"entitlement".equals(objectElement.getAttribute("Id"))) {
            throw new SecurityException("Expected one entitlement inside the signed object");
        }

        DOMValidateContext context = new DOMValidateContext(trustedKey, signatureElement);
        context.setProperty("org.apache.jcp.xml.dsig.secureValidation", Boolean.TRUE);
        context.setIdAttributeNS(objectElement, null, "Id");
        XMLSignature signature = signatureFactory().unmarshalXMLSignature(context);
        SignedInfo signedInfo = signature.getSignedInfo();
        if (signedInfo.getReferences().size() != 1
                || !SignatureMethod.HMAC_SHA256.equals(signedInfo.getSignatureMethod().getAlgorithm())
                || !CanonicalizationMethod.EXCLUSIVE.equals(signedInfo.getCanonicalizationMethod().getAlgorithm())) {
            throw new SecurityException("Unexpected entitlement signature profile");
        }
        Reference reference = signedInfo.getReferences().get(0);
        // Restrict the profile before validation so references cannot trigger external I/O or omit fields.
        if (!"#entitlement".equals(reference.getURI())
                || !DigestMethod.SHA256.equals(reference.getDigestMethod().getAlgorithm())
                || reference.getTransforms().size() != 1
                || !CanonicalizationMethod.EXCLUSIVE.equals(reference.getTransforms().get(0).getAlgorithm())) {
            throw new SecurityException("Expected authentication of the complete entitlement object");
        }
        if (!signature.validate(context)) {
            throw new SecurityException("Entitlement authentication failed");
        }
        return new Entitlement(entitlement.getAttribute("tenant"), entitlement.getAttribute("feature"),
                entitlement.getAttribute("seats"));
    }

    private static XMLSignatureFactory signatureFactory() {
        // Select Santuario explicitly without mutating the JVM-wide provider list.
        return XMLSignatureFactory.getInstance("DOM", new XMLDSigRI());
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

    private record Entitlement(String tenant, String feature, String seats) {
    }
}

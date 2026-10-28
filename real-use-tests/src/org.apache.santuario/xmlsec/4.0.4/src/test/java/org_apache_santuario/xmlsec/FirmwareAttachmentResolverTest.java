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
import org.apache.xml.security.signature.XMLSignatureByteInput;
import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.apache.xml.security.utils.XMLUtils;
import org.apache.xml.security.utils.resolver.ResourceResolverContext;
import org.apache.xml.security.utils.resolver.ResourceResolverException;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises detached-resource resolution to authenticate binary firmware attachments with their XML release metadata.
@Timeout(60)
public class FirmwareAttachmentResolverTest {
    private static final String NAMESPACE = "urn:example:firmware";
    private static final String ATTACHMENT_URI = "cid:controller-firmware@releases.example.test";

    @Test
    public void installsOnlyFirmwareBoundToTheSignedRelease() throws Exception {
        Init.init();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair publisher = generator.generateKeyPair();
        byte[] firmware = {0x46, 0x57, 0, 1, 0, 2, 0x7f, (byte) 0xff};
        byte[] releaseXml = signRelease(publisher, firmware);

        Release accepted = receiveRelease(releaseXml, firmware, publisher.getPublic());
        assertEquals("controller-west", accepted.model());
        assertEquals("2025.04", accepted.version());
        assertArrayEquals(firmware, accepted.firmware());

        byte[] replacedFirmware = firmware.clone();
        replacedFirmware[3] = 9;
        assertThrows(SecurityException.class,
                () -> receiveRelease(releaseXml, replacedFirmware, publisher.getPublic()),
                "A valid XML signature must also authenticate the detached binary attachment");

        byte[] relabeledRelease = new String(releaseXml, StandardCharsets.UTF_8)
                .replace("2025.04", "2025.05").getBytes(StandardCharsets.UTF_8);
        assertThrows(SecurityException.class,
                () -> receiveRelease(relabeledRelease, firmware, publisher.getPublic()),
                "The release version and binary must be authenticated together");
    }

    private static byte[] signRelease(KeyPair publisher, byte[] firmware) throws Exception {
        Document document = parse("""
                <f:Delivery xmlns:f="urn:example:firmware">
                    <f:Release Id="release" model="controller-west" version="2025.04"/>
                </f:Delivery>
                """.getBytes(StandardCharsets.UTF_8));
        Element release = singleElement(document, NAMESPACE, "Release");
        release.setIdAttributeNS(null, "Id", true);
        release.setAttribute("attachment", ATTACHMENT_URI);

        XMLSignature signature = new XMLSignature(document, "",
                XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        document.getDocumentElement().appendChild(signature.getElement());
        Transforms transforms = new Transforms(document);
        transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        signature.addDocument("#release", transforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);
        // Binary attachments are digested as octets, without XML canonicalization.
        signature.addDocument(ATTACHMENT_URI, null, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);
        signature.addResourceResolver(new AttachmentResolver(firmware));
        signature.sign(publisher.getPrivate());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XMLUtils.outputDOM(document, output);
            return output.toByteArray();
        }
    }

    private static Release receiveRelease(byte[] xml, byte[] firmware, PublicKey trustedPublisher) throws Exception {
        // Snapshot the attachment before verification so the installed bytes are exactly those verified.
        byte[] snapshot = firmware.clone();
        Document document = parse(xml);
        Element delivery = document.getDocumentElement();
        Element release = singleElement(document, NAMESPACE, "Release");
        Element signatureElement = singleElement(document, Constants.SignatureSpecNS, "Signature");
        if (!NAMESPACE.equals(delivery.getNamespaceURI()) || !"Delivery".equals(delivery.getLocalName())
                || release.getParentNode() != delivery || signatureElement.getParentNode() != delivery
                || !"release".equals(release.getAttribute("Id"))
                || !ATTACHMENT_URI.equals(release.getAttribute("attachment"))) {
            throw new SecurityException("Unexpected firmware delivery structure");
        }
        release.setIdAttributeNS(null, "Id", true);
        XMLSignature signature = new XMLSignature(signatureElement, "", true);
        // Permit only this delivery's metadata and its supplied attachment, never arbitrary network/file URIs.
        if (signature.getSignedInfo().getLength() != 2
                || !"#release".equals(signature.getSignedInfo().item(0).getURI())
                || !ATTACHMENT_URI.equals(signature.getSignedInfo().item(1).getURI())
                || signature.getSignedInfo().item(1).getTransforms() != null) {
            throw new SecurityException("Expected references to release metadata and untransformed firmware");
        }
        signature.addResourceResolver(new AttachmentResolver(snapshot));
        if (!signature.checkSignatureValue(trustedPublisher)) {
            throw new SecurityException("Firmware delivery authentication failed");
        }
        return new Release(release.getAttribute("model"), release.getAttribute("version"), snapshot);
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

    private record Release(String model, String version, byte[] firmware) {
    }

    // Application resolver for a MIME Content-ID supplied by the delivery's attachment layer.
    private static final class AttachmentResolver extends ResourceResolverSpi {
        private final byte[] firmware;

        private AttachmentResolver(byte[] firmware) {
            this.firmware = firmware.clone();
        }

        @Override
        public boolean engineCanResolveURI(ResourceResolverContext context) {
            return ATTACHMENT_URI.equals(context.uriToResolve);
        }

        @Override
        public XMLSignatureInput engineResolveURI(ResourceResolverContext context) throws ResourceResolverException {
            if (!engineCanResolveURI(context)) {
                throw new ResourceResolverException("utils.resolver.noClass",
                        new Object[]{context.uriToResolve}, context.uriToResolve, context.baseUri);
            }
            XMLSignatureInput input = new XMLSignatureByteInput(firmware);
            input.setSourceURI(ATTACHMENT_URI);
            input.setMIMEType("application/octet-stream");
            input.setSecureValidation(context.secureValidation);
            return input;
        }
    }
}

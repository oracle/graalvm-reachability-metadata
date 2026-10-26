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
import org.apache.xml.security.signature.ObjectContainer;
import org.apache.xml.security.signature.Reference;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.apache.xml.security.utils.XMLUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises XML Signature Base64 transforms for authenticated inspection images embedded in XML deliveries.
@Timeout(60)
public class InspectionAttachmentBase64Test {
    private static final String NAMESPACE = "urn:example:inspection";

    @Test
    public void acceptsRewrappedImageButRejectsChangedBytesOrFilename() throws Exception {
        Init.init();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair inspector = generator.generateKeyPair();
        byte[] image = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aC1sAAAAASUVORK5CYII=");
        byte[] delivery = signInspection(inspector, image);
        Attachment accepted = receiveInspection(delivery, inspector.getPublic());
        assertEquals("inspection-704.png", accepted.filename());
        assertArrayEquals(image, accepted.content());

        // An XML gateway may wrap Base64 text without changing the authenticated binary image.
        Document rewrapped = parse(delivery);
        Element embeddedImage = singleElement(rewrapped, Constants.SignatureSpecNS, "Object");
        embeddedImage.setTextContent("\n" + Base64.getMimeEncoder(24, new byte[]{'\n'})
                .encodeToString(image) + "\n");
        byte[] forwarded = serialize(rewrapped);
        Attachment afterGateway = receiveInspection(forwarded, inspector.getPublic());
        assertEquals(accepted.filename(), afterGateway.filename());
        assertArrayEquals(image, afterGateway.content(),
                "The Base64 transform must authenticate decoded bytes, not transport line breaks");

        Document replacedImage = parse(forwarded);
        byte[] changedImage = image.clone();
        changedImage[changedImage.length - 1] ^= 1;
        singleElement(replacedImage, Constants.SignatureSpecNS, "Object")
                .setTextContent(Base64.getEncoder().encodeToString(changedImage));
        byte[] replaced = serialize(replacedImage);
        assertThrows(SecurityException.class, () -> receiveInspection(replaced, inspector.getPublic()),
                "Replacing even one binary byte must invalidate the inspection attachment");

        Document renamedImage = parse(forwarded);
        singleElement(renamedImage, NAMESPACE, "Image").setAttribute("filename", "approved-705.png");
        byte[] renamed = serialize(renamedImage);
        assertThrows(SecurityException.class, () -> receiveInspection(renamed, inspector.getPublic()),
                "The image's business filename must be authenticated alongside its bytes");
    }

    private static byte[] signInspection(KeyPair inspector, byte[] image) throws Exception {
        Document document = parse("""
                <i:Delivery xmlns:i="urn:example:inspection">
                    <i:Image Id="record" filename="inspection-704.png" attachment="#image"/>
                </i:Delivery>
                """.getBytes(StandardCharsets.UTF_8));
        singleElement(document, NAMESPACE, "Image").setIdAttributeNS(null, "Id", true);
        XMLSignature signature = new XMLSignature(document, "",
                XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        document.getDocumentElement().appendChild(signature.getElement());
        ObjectContainer attachment = new ObjectContainer(document);
        attachment.setId("image");
        attachment.appendChild(document.createTextNode(Base64.getEncoder().encodeToString(image)));
        signature.appendObject(attachment);

        Transforms metadataTransforms = new Transforms(document);
        metadataTransforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        signature.addDocument("#record", metadataTransforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);
        Transforms imageTransforms = new Transforms(document);
        imageTransforms.addTransform(Transforms.TRANSFORM_BASE64_DECODE);
        signature.addDocument("#image", imageTransforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);
        signature.sign(inspector.getPrivate());
        return serialize(document);
    }

    private static Attachment receiveInspection(byte[] delivery, PublicKey trustedInspector) throws Exception {
        Document document = parse(delivery);
        Element root = document.getDocumentElement();
        Element metadata = singleElement(document, NAMESPACE, "Image");
        Element signatureElement = singleElement(document, Constants.SignatureSpecNS, "Signature");
        Element image = singleElement(document, Constants.SignatureSpecNS, "Object");
        if (!NAMESPACE.equals(root.getNamespaceURI()) || !"Delivery".equals(root.getLocalName())
                || metadata.getParentNode() != root || signatureElement.getParentNode() != root
                || image.getParentNode() != signatureElement
                || !"record".equals(metadata.getAttribute("Id"))
                || !"image".equals(image.getAttribute("Id"))
                || !"#image".equals(metadata.getAttribute("attachment"))) {
            throw new SecurityException("Unexpected inspection delivery structure");
        }
        // Require plain Base64 text so verification and application decoding see exactly the same payload.
        for (Node child = image.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.TEXT_NODE && child.getNodeType() != Node.CDATA_SECTION_NODE) {
                throw new SecurityException("The image object must contain only Base64 text");
            }
        }
        metadata.setIdAttributeNS(null, "Id", true);
        image.setIdAttributeNS(null, "Id", true);
        XMLSignature signature = new XMLSignature(signatureElement, "", true);
        if (signature.getSignedInfo().getLength() != 2) {
            throw new SecurityException("Expected authentication of image metadata and binary content");
        }
        // Restrict references and transforms before validation, excluding external resource access.
        requireReference(signature.getSignedInfo().item(0), "#record",
                Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        requireReference(signature.getSignedInfo().item(1), "#image", Transforms.TRANSFORM_BASE64_DECODE);
        if (!signature.checkSignatureValue(trustedInspector)) {
            throw new SecurityException("Inspection attachment authentication failed");
        }
        // Only XML whitespace is insignificant in Base64; do not silently discard arbitrary punctuation.
        String encoded = image.getTextContent().replace(" ", "").replace("\t", "")
                .replace("\r", "").replace("\n", "");
        return new Attachment(metadata.getAttribute("filename"), Base64.getDecoder().decode(encoded));
    }

    private static void requireReference(Reference reference, String uri, String transform) throws Exception {
        Transforms transforms = reference.getTransforms();
        if (!uri.equals(reference.getURI())
                || !MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256.equals(
                        reference.getMessageDigestAlgorithm().getAlgorithmURI())
                || transforms == null || transforms.getLength() != 1
                || !transform.equals(transforms.item(0).getURI())) {
            throw new SecurityException("Unexpected inspection reference profile");
        }
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

    private record Attachment(String filename, byte[] content) {
    }
}

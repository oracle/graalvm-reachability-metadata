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
import org.apache.xml.security.signature.Manifest;
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
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises XML Signature manifests to authenticate all reports in an accounting archive bundle.
@Timeout(60)
public class ArchiveManifestValidationTest {
    private static final String NAMESPACE = "urn:example:accounting-archive";

    @Test
    public void importsOnlyBundlesWhoseManifestAndEveryReportAreAuthenticated() throws Exception {
        Init.init();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair accountant = generator.generateKeyPair();
        byte[] bundle = signArchive(accountant);

        assertEquals(new Archive("Sales: 1250.00 EUR", "Quarter Q1: approved"),
                receiveArchive(bundle, accountant.getPublic()));

        // The manifest itself remains unchanged when either referenced report is altered.
        Document changedLedger = parse(bundle);
        singleElement(changedLedger, NAMESPACE, "Ledger").setTextContent("Sales: 9250.00 EUR");
        byte[] inflatedBundle = serialize(changedLedger);
        assertThrows(SecurityException.class, () -> receiveArchive(inflatedBundle, accountant.getPublic()),
                "A signature over a manifest must also authenticate its referenced ledger");

        Document changedSummary = parse(bundle);
        singleElement(changedSummary, NAMESPACE, "Summary").setTextContent("Quarter Q1: rejected");
        byte[] changedBundle = serialize(changedSummary);
        assertThrows(SecurityException.class, () -> receiveArchive(changedBundle, accountant.getPublic()),
                "Every report in the manifest must be verified before archive import");
    }

    private static byte[] signArchive(KeyPair accountant) throws Exception {
        Document document = parse("""
                <a:Archive xmlns:a="urn:example:accounting-archive">
                    <a:Ledger Id="ledger">Sales: 1250.00 EUR</a:Ledger>
                    <a:Summary Id="summary">Quarter Q1: approved</a:Summary>
                </a:Archive>
                """.getBytes(StandardCharsets.UTF_8));
        singleElement(document, NAMESPACE, "Ledger").setIdAttributeNS(null, "Id", true);
        singleElement(document, NAMESPACE, "Summary").setIdAttributeNS(null, "Id", true);
        XMLSignature signature = new XMLSignature(document, "",
                XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        document.getDocumentElement().appendChild(signature.getElement());

        Manifest manifest = new Manifest(document);
        manifest.setId("archive-manifest");
        ObjectContainer object = new ObjectContainer(document);
        object.appendChild(manifest.getElement());
        signature.appendObject(object);
        manifest.addDocument("", "#ledger", canonicalize(document),
                MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256, null, null);
        manifest.addDocument("", "#summary", canonicalize(document),
                MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256, null, null);
        // Finalize the reports' digest values before signing the manifest that contains them.
        manifest.generateDigestValues();
        // Keep the manifest as a node set for nested same-document reference resolution.
        // The signature's digest calculation applies the default XML canonicalization.
        signature.addDocument("#archive-manifest", null,
                MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256, null, Reference.MANIFEST_URI);
        signature.sign(accountant.getPrivate());
        return serialize(document);
    }

    private static Archive receiveArchive(byte[] bundle, PublicKey trustedAccountant) throws Exception {
        Document document = parse(bundle);
        Element root = document.getDocumentElement();
        Element ledger = singleElement(document, NAMESPACE, "Ledger");
        Element summary = singleElement(document, NAMESPACE, "Summary");
        Element signatureElement = singleElement(document, Constants.SignatureSpecNS, "Signature");
        Element manifestElement = singleElement(document, Constants.SignatureSpecNS, "Manifest");
        Element objectElement = singleElement(document, Constants.SignatureSpecNS, "Object");
        if (!NAMESPACE.equals(root.getNamespaceURI()) || !"Archive".equals(root.getLocalName())
                || ledger.getParentNode() != root || summary.getParentNode() != root
                || signatureElement.getParentNode() != root || objectElement.getParentNode() != signatureElement
                || manifestElement.getParentNode() != objectElement
                || !"ledger".equals(ledger.getAttribute("Id"))
                || !"summary".equals(summary.getAttribute("Id"))
                || !"archive-manifest".equals(manifestElement.getAttribute("Id"))) {
            throw new SecurityException("Unexpected accounting archive structure");
        }
        ledger.setIdAttributeNS(null, "Id", true);
        summary.setIdAttributeNS(null, "Id", true);
        manifestElement.setIdAttributeNS(null, "Id", true);
        XMLSignature signature = new XMLSignature(signatureElement, "", true);
        Manifest manifest = new Manifest(manifestElement, "", true);
        if (signature.getSignedInfo().getLength() != 1 || manifest.getLength() != 2) {
            throw new SecurityException("Expected one signed manifest containing both reports");
        }
        // Restrict all references before validation: no external I/O or transforms that omit business data.
        requireReference(signature.getSignedInfo().item(0), "#archive-manifest", Reference.MANIFEST_URI);
        requireReference(manifest.item(0), "#ledger", "");
        requireReference(manifest.item(1), "#summary", "");
        // Authenticating only the manifest's XML does not authenticate its referenced reports.
        signature.setFollowNestedManifests(true);
        if (!signature.checkSignatureValue(trustedAccountant)) {
            throw new SecurityException("Accounting archive authentication failed");
        }
        return new Archive(ledger.getTextContent(), summary.getTextContent());
    }

    private static void requireReference(Reference reference, String uri, String type) throws Exception {
        Transforms transforms = reference.getTransforms();
        if (!uri.equals(reference.getURI()) || !type.equals(reference.getType())
                || !MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256.equals(
                        reference.getMessageDigestAlgorithm().getAlgorithmURI())) {
            throw new SecurityException("Unexpected archive reference profile");
        }
        if (Reference.MANIFEST_URI.equals(type)) {
            if (transforms != null) {
                throw new SecurityException("Manifest references must preserve the original node set");
            }
        } else if (transforms == null || transforms.getLength() != 1
                || !Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS.equals(transforms.item(0).getURI())) {
            throw new SecurityException("Report references must authenticate the complete report");
        }
    }

    private static Transforms canonicalize(Document document) throws Exception {
        Transforms transforms = new Transforms(document);
        transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        return transforms;
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

    private record Archive(String ledger, String summary) {
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_santuario.xmlsec;

import org.apache.xml.security.Init;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.signature.SignedInfo;
import org.apache.xml.security.signature.Reference;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.encryption.XMLCipher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class XmlsecTest {

    @BeforeAll
    static void initLibrary() {
        // Initialize the library once per JVM to register algorithms and providers.
        Init.init();
    }

    @Test
    void signAndVerifyEnvelopedSignature_RSA_SHA256() throws Exception {
        // Create a simple XML document
        Document doc = newEmptyDocument();
        Element root = doc.createElementNS("urn:test", "t:root");
        root.setAttribute("xmlns:t", "urn:test");
        Element data = doc.createElementNS("urn:test", "t:data");
        data.setTextContent("hello-xmlsec");
        root.appendChild(data);
        doc.appendChild(root);

        // Generate an RSA keypair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();

        // Create XML Signature (enveloped, exclusive c14n, SHA-256 digest, RSA-SHA256 signature)
        String sigAlgUri = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
        XMLSignature sig = new XMLSignature(doc, "", sigAlgUri);
        // Signature element must be inside the signed document (enveloped signature)
        root.appendChild(sig.getElement());

        Transforms transforms = new Transforms(doc);
        transforms.addTransform("http://www.w3.org/2000/09/xmldsig#enveloped-signature");
        transforms.addTransform("http://www.w3.org/2001/10/xml-exc-c14n#");

        String digestAlgUri = "http://www.w3.org/2001/04/xmlenc#sha256";
        sig.addDocument("", transforms, digestAlgUri);

        // Advertise the public key in KeyInfo and sign
        sig.addKeyInfo(kp.getPublic());
        sig.sign(kp.getPrivate());

        // Locate the Signature element and verify the signature against the public key
        Element sigElem = (Element) doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").item(0);
        XMLSignature verifier = new XMLSignature(sigElem, "");
        boolean valid = verifier.checkSignatureValue(kp.getPublic());

        assertThat(valid).as("Signature must verify with the corresponding public key").isTrue();

        // Additional structural assertions: one Reference, two Transforms, SHA-256 digest
        SignedInfo si = verifier.getSignedInfo();
        assertThat(si.getLength()).isEqualTo(1);
        Reference ref = si.item(0);
        assertThat(ref.getTransforms()).isNotNull();
        assertThat(ref.getTransforms().getLength()).isEqualTo(2);
        assertThat(ref.getMessageDigestAlgorithm()).isNotNull();
        assertThat(ref.getMessageDigestAlgorithm().getAlgorithmURI()).isEqualTo(digestAlgUri);
    }

    @Test
    void canonicalizationIsIdempotent_C14N11_omitComments() throws Exception {
        // Build a small XML with namespaces and attributes
        Document doc = newEmptyDocument();
        Element a = doc.createElementNS("urn:ns1", "ns1:a");
        a.setAttribute("xmlns:ns1", "urn:ns1");
        a.setAttribute("attr", "value");
        Element b = doc.createElementNS("urn:ns2", "ns2:b");
        b.setAttribute("xmlns:ns2", "urn:ns2");
        b.setTextContent("text");
        a.appendChild(b);
        doc.appendChild(a);

        // Canonicalize using XML C14N 1.1 without comments
        String c14n11OmitComments = "http://www.w3.org/2006/12/xml-c14n11";
        Canonicalizer canon = Canonicalizer.getInstance(c14n11OmitComments);
        byte[] first = canon.canonicalizeSubtree(doc);

        // Parse the canonical output back and canonicalize again - should be identical (idempotent)
        Document reparsed = parseXml(new String(first, StandardCharsets.UTF_8));
        byte[] second = canon.canonicalizeSubtree(reparsed);

        assertThat(first).as("Canonicalization must be idempotent").isEqualTo(second);
        assertThatNoException().isThrownBy(() -> Canonicalizer.getInstance("http://www.w3.org/2001/10/xml-exc-c14n#"));
    }

    @Test
    void encryptAndDecryptElement_AES128_CBC_roundtrip() throws Exception {
        // Prepare a document with a <secret> element
        Document doc = newEmptyDocument();
        Element root = doc.createElement("root");
        Element secret = doc.createElement("secret");
        String plaintext = "top-secret-data";
        secret.setTextContent(plaintext);
        root.appendChild(secret);
        doc.appendChild(root);

        // Generate an AES-128 key
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(128, new SecureRandom());
        SecretKey aesKey = kg.generateKey();

        // Encrypt the <secret> element entirely (replace element with EncryptedData)
        String encAlgUri = "http://www.w3.org/2001/04/xmlenc#aes128-cbc";
        XMLCipher encCipher = XMLCipher.getInstance(encAlgUri);
        encCipher.init(XMLCipher.ENCRYPT_MODE, aesKey);
        encCipher.doFinal(doc, secret, false);

        // Ensure EncryptedData element is present
        Element encryptedDataElem = firstElementByTagNameNS(doc, "http://www.w3.org/2001/04/xmlenc#", "EncryptedData");
        assertThat(encryptedDataElem).as("EncryptedData should be present after encryption").isNotNull();

        // Decrypt back
        XMLCipher decCipher = XMLCipher.getInstance();
        decCipher.init(XMLCipher.DECRYPT_MODE, aesKey);
        decCipher.doFinal(doc, encryptedDataElem);

        // Verify we got the original <secret> element back with the same content
        Element restoredSecret = (Element) doc.getElementsByTagName("secret").item(0);
        assertThat(restoredSecret).isNotNull();
        assertThat(restoredSecret.getTextContent()).isEqualTo(plaintext);
    }

    // ---- Helpers ----

    private static Document newEmptyDocument() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        // Harden the parser - no external entities
        try {
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Throwable ignore) {
            // Features may not be supported on all runtimes; best-effort hardening.
        }
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.newDocument();
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        try {
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Throwable ignore) {
        }
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static Element firstElementByTagNameNS(Document doc, String ns, String localName) {
        NodeList nl = doc.getElementsByTagNameNS(ns, localName);
        return (nl != null && nl.getLength() > 0) ? (Element) nl.item(0) : null;
    }
}

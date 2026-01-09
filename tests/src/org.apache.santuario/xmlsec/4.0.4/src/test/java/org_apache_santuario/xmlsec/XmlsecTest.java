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
import org.apache.xml.security.encryption.EncryptedKey;
import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.keys.KeyInfo;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class XmlsecTest {

    @BeforeAll
    static void initXmlSec() {
        // Initialize the XML Security library once for all tests.
        Init.init();
    }

    @Test
    void signAndVerifyEnvelopedSignatureRsaSha256() throws Exception {
        // Create a simple namespaced document
        Document doc = newDocument();
        Element root = doc.createElementNS("urn:test", "t:root");
        // Explicitly declare the namespace prefix used in this subtree
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:t", "urn:test");
        root.setAttributeNS("urn:test", "t:attr", "value");
        Element child = doc.createElementNS("urn:test", "t:child");
        child.setTextContent("payload");
        root.appendChild(child);
        doc.appendChild(root);

        // Generate RSA key pair for signing
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();

        // Create XML Signature with Exclusive C14N (omit comments)
        XMLSignature signature = new XMLSignature(
                doc,
                "",
                XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS
        );

        // Append the Signature element into the document
        root.appendChild(signature.getElement());

        // Add reference to the whole document with the Enveloped Signature and Exclusive C14N transforms
        Transforms transforms = new Transforms(doc);
        transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
        transforms.addTransform(Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        signature.addDocument("", transforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);

        // Sign using the private key
        signature.sign(kp.getPrivate());

        // Ensure a Signature element is present
        assertThat(root.getElementsByTagNameNS(Constants.SignatureSpecNS, "Signature").getLength()).isEqualTo(1);

        // Verify signature using the public key
        XMLSignature parsedSignature = new XMLSignature(signature.getElement(), "");
        boolean valid = parsedSignature.checkSignatureValue(kp.getPublic());
        assertThat(valid).isTrue();

        // Re-serialize and reparse to ensure the signature remains valid across parsing cycles
        byte[] xmlBytes = toBytes(doc);
        Document reparsed = parseBytes(xmlBytes);
        Element sigElem = (Element) reparsed.getElementsByTagNameNS(Constants.SignatureSpecNS, "Signature").item(0);
        XMLSignature reparsedSig = new XMLSignature(sigElem, "");
        assertThat(reparsedSig.checkSignatureValue(kp.getPublic())).isTrue();
    }

    @Test
    void exclusiveCanonicalizationProducesStableOutput() throws Exception {
        // Build two semantically equivalent documents with same prefix and different attribute ordering
        Document d1 = newDocument();
        Element r1 = d1.createElementNS("urn:test", "t:root");
        r1.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:t", "urn:test");
        r1.setAttributeNS("urn:test", "t:z", "3");
        r1.setAttributeNS("urn:test", "t:a", "1");
        Element c1 = d1.createElementNS("urn:test", "t:child");
        c1.setTextContent("text");
        r1.appendChild(c1);
        d1.appendChild(r1);

        Document d2 = newDocument();
        Element r2 = d2.createElementNS("urn:test", "t:root");
        r2.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:t", "urn:test");
        r2.setAttributeNS("urn:test", "t:a", "1");
        r2.setAttributeNS("urn:test", "t:z", "3");
        Element c2 = d2.createElementNS("urn:test", "t:child");
        c2.setTextContent("text");
        r2.appendChild(c2);
        d2.appendChild(r2);

        Canonicalizer canon = Canonicalizer.getInstance(Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

        ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
        canon.canonicalizeSubtree(r1, bos1);
        byte[] out1 = bos1.toByteArray();

        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        canon.canonicalizeSubtree(r2, bos2);
        byte[] out2 = bos2.toByteArray();

        // The canonicalized outputs must match despite different attribute insertion order
        assertThat(new String(out1, StandardCharsets.UTF_8)).isEqualTo(new String(out2, StandardCharsets.UTF_8));
    }

    @Test
    void encryptAndDecryptElementWithAes128() throws Exception {
        // Build a document with a secret element
        Document doc = newDocument();
        Element root = doc.createElementNS("urn:test", "t:root");
        // Ensure the 't' prefix is declared so subtree serialization during encryption/decryption is valid
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:t", "urn:test");
        Element secret = doc.createElementNS("urn:test", "t:Secret");
        secret.setTextContent("super-secret");
        Element other = doc.createElementNS("urn:test", "t:Other");
        other.setTextContent("public");
        root.appendChild(secret);
        root.appendChild(other);
        doc.appendChild(root);

        // Generate an AES-128 key
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(128, new SecureRandom());
        SecretKey key = kg.generateKey();

        // Encrypt the Secret element (element encryption)
        XMLCipher encCipher = XMLCipher.getInstance(XMLCipher.AES_128);
        encCipher.init(XMLCipher.ENCRYPT_MODE, key);
        encCipher.doFinal(doc, secret, false);

        // After encryption, there should be no "Secret" element under root
        assertThat(root.getElementsByTagNameNS("urn:test", "Secret").getLength()).isEqualTo(0);
        // And there should be exactly one EncryptedData element
        assertThat(root.getElementsByTagNameNS("http://www.w3.org/2001/04/xmlenc#", "EncryptedData").getLength()).isEqualTo(1);

        // Decrypt back
        Element encryptedData = (Element) root.getElementsByTagNameNS("http://www.w3.org/2001/04/xmlenc#", "EncryptedData").item(0);
        XMLCipher decCipher = XMLCipher.getInstance();
        decCipher.init(XMLCipher.DECRYPT_MODE, key);
        decCipher.doFinal(doc, encryptedData);

        // The Secret element should be restored with original content
        Element restored = (Element) root.getElementsByTagNameNS("urn:test", "Secret").item(0);
        assertThat(restored).isNotNull();
        assertThat(restored.getTextContent()).isEqualTo("super-secret");
        // The 'Other' element remains unchanged
        assertThat(((Element) root.getElementsByTagNameNS("urn:test", "Other").item(0)).getTextContent()).isEqualTo("public");
    }

    @Test
    void hmacSha256SignatureOnElementById_isValidAndDetectsTampering() throws Exception {
        // Build a document with a target element identified by ID
        Document doc = newDocument();
        Element root = doc.createElementNS("urn:test", "t:root");
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:t", "urn:test");

        Element data = doc.createElementNS("urn:test", "t:Data");
        data.setTextContent("important");
        data.setAttribute("Id", "elem-1");
        // Mark the attribute as type ID so same-document URI can resolve
        data.setIdAttribute("Id", true);

        Element other = doc.createElementNS("urn:test", "t:Other");
        other.setTextContent("not-signed");

        root.appendChild(data);
        root.appendChild(other);
        doc.appendChild(root);

        // Create an HMAC-SHA256 XML Signature (detached same-document reference)
        XMLSignature signature = new XMLSignature(
                doc,
                "",
                XMLSignature.ALGO_ID_MAC_HMAC_SHA256,
                Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS
        );
        root.appendChild(signature.getElement());

        // Reference only the Data element via its ID and canonicalize it
        Transforms transforms = new Transforms(doc);
        transforms.addTransform(Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        signature.addDocument("#elem-1", transforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);

        // Generate an HMAC key and sign
        SecretKey hmacKey = KeyGenerator.getInstance("HmacSHA256").generateKey();
        signature.sign(hmacKey);

        // Verify signature is valid with the same HMAC key
        XMLSignature parsedSignature = new XMLSignature(signature.getElement(), "");
        assertThat(parsedSignature.checkSignatureValue(hmacKey)).isTrue();

        // Tamper with the signed element content; verification must fail now
        data.setTextContent("tampered");
        XMLSignature tamperedSig = new XMLSignature(signature.getElement(), "");
        assertThat(tamperedSig.checkSignatureValue(hmacKey)).isFalse();
    }

    @Test
    void encryptElementWithAes128AndWrapKeyWithRsaOaep() throws Exception {
        // Build a document that contains data to encrypt
        Document doc = newDocument();
        Element root = doc.createElementNS("urn:test", "t:root");
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:t", "urn:test");
        Element sensitive = doc.createElementNS("urn:test", "t:Sensitive");
        sensitive.setTextContent("classified");
        Element publicElem = doc.createElementNS("urn:test", "t:Public");
        publicElem.setTextContent("public-info");
        root.appendChild(sensitive);
        root.appendChild(publicElem);
        doc.appendChild(root);

        // Generate RSA key pair for key transport (wrapping AES key)
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        KeyPair rsaKp = kpg.generateKeyPair();

        // Generate a random AES-128 content encryption key
        SecretKey aesKey = KeyGenerator.getInstance("AES").generateKey();

        // Prepare data cipher for element encryption
        XMLCipher dataCipher = XMLCipher.getInstance(XMLCipher.AES_128);
        dataCipher.init(XMLCipher.ENCRYPT_MODE, aesKey);

        // Wrap AES key using RSA-OAEP and place it into EncryptedData/KeyInfo
        XMLCipher keyCipher = XMLCipher.getInstance(XMLCipher.RSA_OAEP);
        keyCipher.init(XMLCipher.WRAP_MODE, rsaKp.getPublic());
        EncryptedKey encryptedKey = keyCipher.encryptKey(doc, aesKey);

        KeyInfo ki = new KeyInfo(doc);
        ki.add(encryptedKey);
        dataCipher.getEncryptedData().setKeyInfo(ki);

        // Encrypt the element (element encryption, not content-only)
        dataCipher.doFinal(doc, sensitive, false);

        // Assertions after encryption
        assertThat(root.getElementsByTagNameNS("urn:test", "Sensitive").getLength()).isEqualTo(0);
        Element encryptedData = (Element) root.getElementsByTagNameNS("http://www.w3.org/2001/04/xmlenc#", "EncryptedData").item(0);
        assertThat(encryptedData).isNotNull();
        // Ensure an EncryptedKey is embedded via KeyInfo
        assertThat(encryptedData.getElementsByTagNameNS("http://www.w3.org/2001/04/xmlenc#", "EncryptedKey").getLength()).isEqualTo(1);

        // Decrypt using the private RSA key to unwrap the AES key
        XMLCipher decryptCipher = XMLCipher.getInstance();
        decryptCipher.init(XMLCipher.DECRYPT_MODE, null);
        decryptCipher.setKEK(rsaKp.getPrivate());
        decryptCipher.doFinal(doc, encryptedData);

        // After decryption, original element and content must be restored
        Element restored = (Element) root.getElementsByTagNameNS("urn:test", "Sensitive").item(0);
        assertThat(restored).isNotNull();
        assertThat(restored.getTextContent()).isEqualTo("classified");
        // Unrelated element remains unchanged
        assertThat(((Element) root.getElementsByTagNameNS("urn:test", "Public").item(0)).getTextContent()).isEqualTo("public-info");
    }

    // Utility methods

    private static Document newDocument() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        // Harden the parser a bit for safety in tests
        try {
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
            // If not supported by the JAXP implementation, ignore for tests
        }
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.newDocument();
    }

    private static byte[] toBytes(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        t.transform(new DOMSource(doc), new StreamResult(baos));
        return baos.toByteArray();
    }

    private static Document parseBytes(byte[] data) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        try {
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
        }
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new ByteArrayInputStream(data));
    }
}

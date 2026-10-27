/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_santuario.xmlsec;

import org.apache.xml.security.Init;
import org.apache.xml.security.encryption.EncryptedData;
import org.apache.xml.security.encryption.EncryptedKey;
import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.encryption.XMLEncryptionException;
import org.apache.xml.security.keys.KeyInfo;
import org.apache.xml.security.utils.EncryptionConstants;
import org.apache.xml.security.utils.XMLUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises XML Encryption RSA-OAEP key transport for confidential audit reports sent to an external reviewer.
@Timeout(60)
public class AuditReportKeyTransportTest {
    private static final String NAMESPACE = "urn:example:audit";

    @Test
    public void deliversReportUsingOnlyTheReviewersPublicKey() throws Exception {
        Init.init();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair reviewerKeys = generator.generateKeyPair();

        // The sender knows only the reviewer's provisioned public key, not a shared document key.
        byte[] delivery = encryptReport(reviewerKeys.getPublic());
        String wireXml = new String(delivery, StandardCharsets.UTF_8);
        assertFalse(wireXml.contains("Unreconciled balance: 7250.00 EUR"));
        Document routed = parse(delivery);
        assertEquals("AUDIT-704", routed.getDocumentElement().getAttribute("number"));
        assertEquals(0, routed.getElementsByTagNameNS(NAMESPACE, "Findings").getLength());
        assertEquals(1, routed.getElementsByTagNameNS(EncryptionConstants.EncryptionSpecNS,
                "EncryptedKey").getLength());

        assertEquals(new Report("AUDIT-704", "Unreconciled balance: 7250.00 EUR"),
                decryptReport(delivery, reviewerKeys.getPrivate()));

        KeyPair unrelatedReviewer = generator.generateKeyPair();
        assertThrows(XMLEncryptionException.class,
                () -> decryptReport(delivery, unrelatedReviewer.getPrivate()),
                "A different reviewer's private key must not recover the report's document key");
    }

    private static byte[] encryptReport(PublicKey reviewerKey) throws Exception {
        Document document = parse("""
                <a:Report xmlns:a="urn:example:audit" number="AUDIT-704">
                    <a:Findings>Unreconciled balance: 7250.00 EUR</a:Findings>
                </a:Report>
                """.getBytes(StandardCharsets.UTF_8));
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey documentKey = generator.generateKey();

        XMLCipher keyTransport = XMLCipher.getInstance(XMLCipher.RSA_OAEP_11, null, XMLCipher.SHA256);
        keyTransport.init(XMLCipher.WRAP_MODE, reviewerKey);
        EncryptedKey wrappedKey = keyTransport.encryptKey(document, documentKey,
                EncryptionConstants.MGF1_SHA256, null);
        wrappedKey.setRecipient("external-reviewer");

        XMLCipher encryptor = XMLCipher.getInstance(XMLCipher.AES_128_GCM);
        encryptor.init(XMLCipher.ENCRYPT_MODE, documentKey);
        KeyInfo keyInfo = new KeyInfo(document);
        keyInfo.add(wrappedKey);
        encryptor.getEncryptedData().setKeyInfo(keyInfo);
        Element findings = (Element) document.getElementsByTagNameNS(NAMESPACE, "Findings").item(0);
        encryptor.doFinal(document, findings);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XMLUtils.outputDOM(document, output);
            return output.toByteArray();
        }
    }

    private static Report decryptReport(byte[] delivery, PrivateKey reviewerKey) throws Exception {
        Document document = parse(delivery);
        if (document.getElementsByTagNameNS(EncryptionConstants.EncryptionSpecNS,
                "EncryptedData").getLength() != 1) {
            throw new SecurityException("Expected one encrypted audit findings element");
        }
        Element encryptedElement = (Element) document.getElementsByTagNameNS(
                EncryptionConstants.EncryptionSpecNS, "EncryptedData").item(0);
        XMLCipher decryptor = XMLCipher.getInstance();
        decryptor.setSecureValidation(true);
        decryptor.init(XMLCipher.DECRYPT_MODE, null);
        EncryptedData encryptedData = decryptor.loadEncryptedData(document, encryptedElement);
        if (!XMLCipher.AES_128_GCM.equals(encryptedData.getEncryptionMethod().getAlgorithm())) {
            throw new SecurityException("Expected authenticated report encryption");
        }
        EncryptedKey wrappedKey = encryptedData.getKeyInfo().itemEncryptedKey(0);
        if (wrappedKey == null || !"external-reviewer".equals(wrappedKey.getRecipient())
                || !XMLCipher.RSA_OAEP_11.equals(wrappedKey.getEncryptionMethod().getAlgorithm())
                || !XMLCipher.SHA256.equals(wrappedKey.getEncryptionMethod().getDigestAlgorithm())
                || !EncryptionConstants.MGF1_SHA256.equals(wrappedKey.getEncryptionMethod().getMGFAlgorithm())) {
            throw new SecurityException("Expected the reviewer's RSA-OAEP document key");
        }

        // Only the recipient's locally held private key can unwrap the transported AES key.
        XMLCipher keyTransport = XMLCipher.getInstance();
        keyTransport.setSecureValidation(true);
        keyTransport.init(XMLCipher.UNWRAP_MODE, reviewerKey);
        Key documentKey = keyTransport.decryptKey(wrappedKey, XMLCipher.AES_128_GCM);
        decryptor.init(XMLCipher.DECRYPT_MODE, documentKey);
        decryptor.doFinal(document, encryptedElement);
        Element findings = (Element) document.getElementsByTagNameNS(NAMESPACE, "Findings").item(0);
        return new Report(document.getDocumentElement().getAttribute("number"), findings.getTextContent());
    }

    private static Document parse(byte[] xml) throws Exception {
        try (ByteArrayInputStream input = new ByteArrayInputStream(xml)) {
            return XMLUtils.read(input, true);
        }
    }

    private record Report(String number, String findings) {
    }
}

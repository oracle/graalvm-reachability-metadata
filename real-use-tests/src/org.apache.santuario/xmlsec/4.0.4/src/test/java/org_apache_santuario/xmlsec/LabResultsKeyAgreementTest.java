/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_santuario.xmlsec;

import org.apache.xml.security.Init;
import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.apache.xml.security.encryption.AgreementMethod;
import org.apache.xml.security.encryption.CipherData;
import org.apache.xml.security.encryption.EncryptedData;
import org.apache.xml.security.encryption.EncryptedKey;
import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.encryption.XMLEncryptionException;
import org.apache.xml.security.encryption.keys.KeyInfoEnc;
import org.apache.xml.security.encryption.params.ConcatKDFParams;
import org.apache.xml.security.encryption.params.KeyAgreementParameters;
import org.apache.xml.security.keys.KeyInfo;
import org.apache.xml.security.utils.EncryptionConstants;
import org.apache.xml.security.utils.XMLUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
import java.security.spec.ECGenParameterSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises XML Encryption ECDH-ES key agreement for laboratory results delivered to a clinic.
@Timeout(60)
public class LabResultsKeyAgreementTest {
    private static final String NAMESPACE = "urn:example:lab-results";

    @Test
    public void deliversConfidentialResultsUsingTheClinicsEcPublicKey() throws Exception {
        Init.init();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair clinic = generator.generateKeyPair();

        // The laboratory has the clinic's public key, but neither its private key nor a shared secret.
        byte[] delivery = sendResults(clinic.getPublic());
        String wireXml = new String(delivery, StandardCharsets.UTF_8);
        assertFalse(wireXml.contains("PATIENT-704"));
        assertFalse(wireXml.contains("Hemoglobin: 14.2 g/dL"));
        Document routed = parse(delivery);
        assertEquals("LAB-704", routed.getDocumentElement().getAttribute("tracking"));
        assertEquals(0, routed.getElementsByTagNameNS(NAMESPACE, "Results").getLength());
        assertEquals(new Results("PATIENT-704", "Hemoglobin: 14.2 g/dL"),
                receiveResults(delivery, clinic.getPrivate()));

        KeyPair otherClinic = generator.generateKeyPair();
        assertThrows(XMLEncryptionException.class,
                () -> receiveResults(delivery, otherClinic.getPrivate()),
                "Another clinic must not derive the key needed to recover these laboratory results");
    }

    private static byte[] sendResults(PublicKey clinicKey) throws Exception {
        Document document = parse("""
                <l:Delivery xmlns:l="urn:example:lab-results" tracking="LAB-704">
                    <l:Results patient="PATIENT-704">Hemoglobin: 14.2 g/dL</l:Results>
                </l:Delivery>
                """.getBytes(StandardCharsets.UTF_8));
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey resultKey = generator.generateKey();

        // Santuario generates an ephemeral originator key and carries its public part in AgreementMethod.
        ConcatKDFParams derivation = ConcatKDFParams.createBuilder(128,
                MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256).build();
        KeyAgreementParameters agreement = new KeyAgreementParameters(
                KeyAgreementParameters.ActorType.ORIGINATOR,
                EncryptionConstants.ALGO_ID_KEYAGREEMENT_ECDH_ES, derivation);
        XMLCipher wrapper = XMLCipher.getInstance(XMLCipher.AES_128_KeyWrap);
        wrapper.setSecureValidation(true);
        wrapper.init(XMLCipher.WRAP_MODE, clinicKey);
        EncryptedKey wrappedKey = wrapper.encryptKey(document, resultKey, agreement, null);
        wrappedKey.setRecipient("clinic-west");

        XMLCipher encryptor = XMLCipher.getInstance(XMLCipher.AES_128_GCM);
        encryptor.init(XMLCipher.ENCRYPT_MODE, resultKey);
        KeyInfo keyInfo = new KeyInfo(document);
        keyInfo.add(wrappedKey);
        encryptor.getEncryptedData().setKeyInfo(keyInfo);
        encryptor.doFinal(document, singleElement(document, NAMESPACE, "Results"));
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XMLUtils.outputDOM(document, output);
            return output.toByteArray();
        }
    }

    private static Results receiveResults(byte[] delivery, PrivateKey clinicKey) throws Exception {
        Document document = parse(delivery);
        Element root = document.getDocumentElement();
        Element encrypted = singleElement(document, EncryptionConstants.EncryptionSpecNS, "EncryptedData");
        if (!NAMESPACE.equals(root.getNamespaceURI()) || !"Delivery".equals(root.getLocalName())
                || encrypted.getParentNode() != root) {
            throw new SecurityException("Unexpected laboratory delivery structure");
        }
        XMLCipher decryptor = XMLCipher.getInstance();
        decryptor.setSecureValidation(true);
        decryptor.init(XMLCipher.DECRYPT_MODE, null);
        EncryptedData data = decryptor.loadEncryptedData(document, encrypted);
        if (!XMLCipher.AES_128_GCM.equals(data.getEncryptionMethod().getAlgorithm())
                || data.getCipherData().getDataType() != CipherData.VALUE_TYPE
                || data.getKeyInfo() == null) {
            throw new SecurityException("Expected authenticated encrypted results with an inline wrapped key");
        }
        Element keyElement = singleElement(document, EncryptionConstants.EncryptionSpecNS, "EncryptedKey");
        if (keyElement.getParentNode() != data.getKeyInfo().getElement()) {
            throw new SecurityException("The wrapped key must be inside the encrypted results' KeyInfo");
        }
        EncryptedKey wrappedKey = data.getKeyInfo().itemEncryptedKey(0);
        if (!"clinic-west".equals(wrappedKey.getRecipient())
                || !XMLCipher.AES_128_KeyWrap.equals(wrappedKey.getEncryptionMethod().getAlgorithm())
                || wrappedKey.getCipherData().getDataType() != CipherData.VALUE_TYPE
                || !(wrappedKey.getKeyInfo() instanceof KeyInfoEnc keyInfo)
                || keyInfo.lengthAgreementMethod() != 1) {
            throw new SecurityException("Expected an AES-wrapped result key for this clinic");
        }
        AgreementMethod agreement = keyInfo.itemAgreementMethod(0);
        if (!EncryptionConstants.ALGO_ID_KEYAGREEMENT_ECDH_ES.equals(agreement.getAlgorithm())
                || agreement.getOriginatorKeyInfo() == null
                || agreement.getOriginatorKeyInfo().lengthKeyValue() != 1
                || agreement.getOriginatorKeyInfo().lengthRetrievalMethod() != 0
                || agreement.getOriginatorKeyInfo().lengthKeyInfoReference() != 0) {
            throw new SecurityException("Expected ECDH-ES with an inline ephemeral originator public key");
        }

        // Agreement metadata supplies public parameters, not the clinic's locally held private key.
        XMLCipher unwrapper = XMLCipher.getInstance();
        unwrapper.setSecureValidation(true);
        unwrapper.init(XMLCipher.UNWRAP_MODE, clinicKey);
        Key resultKey = unwrapper.decryptKey(wrappedKey, XMLCipher.AES_128_GCM);
        decryptor.init(XMLCipher.DECRYPT_MODE, resultKey);
        decryptor.doFinal(document, encrypted);
        Element results = singleElement(document, NAMESPACE, "Results");
        if (results.getParentNode() != root) {
            throw new SecurityException("Results must occupy the decrypted delivery slot");
        }
        return new Results(results.getAttribute("patient"), results.getTextContent());
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

    private record Results(String patient, String finding) {
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;

import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenSSLECPrivateKeyTest {

    @Test
    void serializesAndDeserializesConscryptEcPrivateKeys() throws Exception {
        KeyPair originalKeyPair = generateEcKeyPair();
        ECPrivateKey conscryptPrivateKey = generateConscryptPrivateKey(originalKeyPair);

        assertThat(conscryptPrivateKey.getClass().getName()).isEqualTo("org.conscrypt.OpenSSLECPrivateKey");

        byte[] serializedPrivateKey = serialize(conscryptPrivateKey);
        ECPrivateKey deserializedPrivateKey = deserialize(serializedPrivateKey);
        byte[] signature = sign(deserializedPrivateKey, "open-ssl-ec-private-key-round-trip");

        assertThat(deserializedPrivateKey.getClass().getName()).isEqualTo("org.conscrypt.OpenSSLECPrivateKey");
        assertThat(deserializedPrivateKey.getAlgorithm()).isEqualTo("EC");
        assertThat(deserializedPrivateKey.getFormat()).isEqualTo("PKCS#8");
        assertThat(deserializedPrivateKey.getEncoded()).containsExactly(conscryptPrivateKey.getEncoded());
        assertThat(deserializedPrivateKey.getS()).isEqualTo(conscryptPrivateKey.getS());
        assertThat(deserializedPrivateKey.getParams().getCurve()).isEqualTo(conscryptPrivateKey.getParams().getCurve());
        assertThat(deserializedPrivateKey.getParams().getGenerator()).isEqualTo(conscryptPrivateKey.getParams().getGenerator());
        assertThat(deserializedPrivateKey.getParams().getOrder()).isEqualTo(conscryptPrivateKey.getParams().getOrder());
        assertThat(deserializedPrivateKey.getParams().getCofactor()).isEqualTo(conscryptPrivateKey.getParams().getCofactor());
        assertThat(verify(originalKeyPair, signature, "open-ssl-ec-private-key-round-trip")).isTrue();
    }

    private static KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        return keyPairGenerator.generateKeyPair();
    }

    private static ECPrivateKey generateConscryptPrivateKey(KeyPair keyPair) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("EC", Conscrypt.newProvider());
        return (ECPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyPair.getPrivate().getEncoded()));
    }

    private static byte[] serialize(PrivateKey privateKey) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return outputStream.toByteArray();
    }

    private static ECPrivateKey deserialize(byte[] serializedPrivateKey) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serializedPrivateKey))) {
            return (ECPrivateKey) objectInputStream.readObject();
        }
    }

    private static byte[] sign(PrivateKey privateKey, String message) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA", Conscrypt.newProvider());
        signature.initSign(privateKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return signature.sign();
    }

    private static boolean verify(KeyPair keyPair, byte[] signatureBytes, String message) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initVerify(keyPair.getPublic());
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return signature.verify(signatureBytes);
    }
}

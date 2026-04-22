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
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenSSLECPublicKeyTest {

    @Test
    void serializesAndDeserializesConscryptEcPublicKeys() throws Exception {
        KeyPair originalKeyPair = generateEcKeyPair();
        ECPublicKey conscryptPublicKey = generateConscryptPublicKey(originalKeyPair);

        assertThat(conscryptPublicKey.getClass().getName()).isEqualTo("org.conscrypt.OpenSSLECPublicKey");

        byte[] serializedPublicKey = serialize(conscryptPublicKey);
        ECPublicKey deserializedPublicKey = deserialize(serializedPublicKey);
        byte[] signature = sign(originalKeyPair, "open-ssl-ec-public-key-round-trip");

        assertThat(deserializedPublicKey.getClass().getName()).isEqualTo("org.conscrypt.OpenSSLECPublicKey");
        assertThat(deserializedPublicKey.getAlgorithm()).isEqualTo("EC");
        assertThat(deserializedPublicKey.getFormat()).isEqualTo("X.509");
        assertThat(deserializedPublicKey.getEncoded()).containsExactly(conscryptPublicKey.getEncoded());
        assertThat(deserializedPublicKey.getW()).isEqualTo(conscryptPublicKey.getW());
        assertThat(deserializedPublicKey.getParams().getCurve()).isEqualTo(conscryptPublicKey.getParams().getCurve());
        assertThat(deserializedPublicKey.getParams().getGenerator()).isEqualTo(conscryptPublicKey.getParams().getGenerator());
        assertThat(deserializedPublicKey.getParams().getOrder()).isEqualTo(conscryptPublicKey.getParams().getOrder());
        assertThat(deserializedPublicKey.getParams().getCofactor()).isEqualTo(conscryptPublicKey.getParams().getCofactor());
        assertThat(verify(deserializedPublicKey, signature, "open-ssl-ec-public-key-round-trip")).isTrue();
    }

    private static KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        return keyPairGenerator.generateKeyPair();
    }

    private static ECPublicKey generateConscryptPublicKey(KeyPair keyPair) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("EC", Conscrypt.newProvider());
        return (ECPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(keyPair.getPublic().getEncoded()));
    }

    private static byte[] serialize(PublicKey publicKey) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return outputStream.toByteArray();
    }

    private static ECPublicKey deserialize(byte[] serializedPublicKey) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serializedPublicKey))) {
            return (ECPublicKey) objectInputStream.readObject();
        }
    }

    private static byte[] sign(KeyPair keyPair, String message) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return signature.sign();
    }

    private static boolean verify(PublicKey publicKey, byte[] signatureBytes, String message) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA", Conscrypt.newProvider());
        signature.initVerify(publicKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return signature.verify(signatureBytes);
    }
}

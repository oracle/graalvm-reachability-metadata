/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class OpenSSLECPublicKeyTest {
    @Test
    void ecPublicKeyRoundTripsThroughJavaSerialization() throws Exception {
        Conscrypt.checkAvailability();
        Provider provider = Conscrypt.newProvider();
        assertThat(Conscrypt.isConscrypt(provider)).isTrue();

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", provider);
        generator.initialize(256, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        assertThat(keyPair.getPublic()).isInstanceOf(ECPublicKey.class);
        assertThat(keyPair.getPublic().getClass().getName())
                .isEqualTo("org.conscrypt.OpenSSLECPublicKey");

        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
        Object deserialized = deserialize(serialize(publicKey));

        assertThat(deserialized).isInstanceOf(ECPublicKey.class);
        assertThat(deserialized.getClass().getName())
                .isEqualTo("org.conscrypt.OpenSSLECPublicKey");
        ECPublicKey restoredPublicKey = (ECPublicKey) deserialized;
        assertThat(restoredPublicKey.getAlgorithm()).isEqualTo("EC");
        assertThat(restoredPublicKey.getFormat()).isEqualTo("X.509");
        assertThat(restoredPublicKey.getEncoded()).isEqualTo(publicKey.getEncoded());
        assertThat(restoredPublicKey.getW()).isEqualTo(publicKey.getW());
        assertEquivalentParameters(restoredPublicKey.getParams(), publicKey.getParams());
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
            stream.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return stream.readObject();
        }
    }

    private static void assertEquivalentParameters(ECParameterSpec actual,
            ECParameterSpec expected) {
        assertThat(actual.getCurve()).isEqualTo(expected.getCurve());
        assertThat(actual.getGenerator()).isEqualTo(expected.getGenerator());
        assertThat(actual.getOrder()).isEqualTo(expected.getOrder());
        assertThat(actual.getCofactor()).isEqualTo(expected.getCofactor());
    }
}

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
import java.security.PrivateKey;
import java.security.Provider;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class OpenSSLECPrivateKeyTest {
    @Test
    void serializationRoundTripRestoresGeneratedEcPrivateKey() throws Exception {
        Provider provider = Conscrypt.newProvider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", provider);
        generator.initialize(new ECGenParameterSpec("prime256v1"));
        KeyPair keyPair = generator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        ECPrivateKey ecPrivateKey = (ECPrivateKey) privateKey;

        assertThat(privateKey.getClass().getName())
                .isEqualTo("org.conscrypt.OpenSSLECPrivateKey");

        Object deserialized = deserialize(serialize(privateKey));

        assertThat(deserialized).isInstanceOf(ECPrivateKey.class);
        ECPrivateKey deserializedKey = (ECPrivateKey) deserialized;
        assertThat(deserializedKey).isNotSameAs(privateKey);
        assertThat(deserializedKey.getAlgorithm()).isEqualTo(privateKey.getAlgorithm());
        assertThat(deserializedKey.getFormat()).isEqualTo(privateKey.getFormat());
        assertThat(deserializedKey.getEncoded()).isEqualTo(privateKey.getEncoded());
        assertThat(deserializedKey.getParams().getCurve())
                .isEqualTo(ecPrivateKey.getParams().getCurve());
        assertThat(deserializedKey.getS()).isEqualTo(ecPrivateKey.getS());
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
            stream.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream stream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return stream.readObject();
        }
    }
}

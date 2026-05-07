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
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class OpenSSLECPublicKeyTest {
    @Test
    void serializationRoundTripRestoresGeneratedEcPublicKey() throws Exception {
        Provider provider = Conscrypt.newProvider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", provider);
        generator.initialize(new ECGenParameterSpec("prime256v1"));
        KeyPair keyPair = generator.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        ECPublicKey ecPublicKey = (ECPublicKey) publicKey;

        assertThat(publicKey.getClass().getName())
                .isEqualTo("org.conscrypt.OpenSSLECPublicKey");

        Object deserialized = deserialize(serialize(publicKey));

        assertThat(deserialized).isInstanceOf(ECPublicKey.class);
        ECPublicKey deserializedKey = (ECPublicKey) deserialized;
        assertThat(deserializedKey).isNotSameAs(publicKey);
        assertThat(deserializedKey.getAlgorithm()).isEqualTo(publicKey.getAlgorithm());
        assertThat(deserializedKey.getFormat()).isEqualTo(publicKey.getFormat());
        assertThat(deserializedKey.getEncoded()).isEqualTo(publicKey.getEncoded());
        assertThat(deserializedKey.getParams().getCurve())
                .isEqualTo(ecPublicKey.getParams().getCurve());
        assertThat(deserializedKey.getW()).isEqualTo(ecPublicKey.getW());
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

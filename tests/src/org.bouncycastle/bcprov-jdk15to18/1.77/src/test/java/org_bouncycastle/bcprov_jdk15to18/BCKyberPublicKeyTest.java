/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.PublicKey;

import org.bouncycastle.pqc.crypto.crystals.kyber.KyberParameters;
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberPublicKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.kyber.BCKyberPublicKey;
import org.junit.jupiter.api.Test;

public class BCKyberPublicKeyTest {
    @Test
    void javaSerializationPreservesKyberPublicKeyEncoding() throws Exception {
        BCKyberPublicKey publicKey = createPublicKey();

        byte[] serialized = serialize(publicKey);
        BCKyberPublicKey restored = deserialize(serialized, publicKey.getEncoded());

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    @Test
    void javaSerializationPreservesKyberPublicKeyWhenNestedAsPublicKey() throws Exception {
        BCKyberPublicKey publicKey = createPublicKey();

        byte[] serialized = serialize(new PublicKeyHolder(publicKey));
        PublicKeyHolder restored = (PublicKeyHolder)deserializeObject(serialized);

        assertPublicKeyRoundTrip(publicKey, (BCKyberPublicKey)restored.publicKey);
    }

    @Test
    void javaUnsharedSerializationPreservesKyberPublicKeyEncoding() throws Exception {
        BCKyberPublicKey publicKey = createPublicKey();

        byte[] serialized = serializeUnshared(publicKey);
        BCKyberPublicKey restored = deserializeUnshared(serialized);

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    private static void assertPublicKeyRoundTrip(
            BCKyberPublicKey publicKey,
            BCKyberPublicKey restored) {
        assertEquals("KYBER512", publicKey.getAlgorithm());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("X.509", restored.getFormat());
        assertEquals(publicKey.getParameterSpec().getName(), restored.getParameterSpec().getName());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
    }

    private static BCKyberPublicKey createPublicKey() {
        return new BCKyberPublicKey(new KyberPublicKeyParameters(
                KyberParameters.kyber512,
                sequence(768, 1),
                sequence(32, 129)));
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(Object object) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(object);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCKyberPublicKey deserialize(
            byte[] serialized,
            byte[] expectedEncoded) throws Exception {
        BCKyberPublicKey publicKey = (BCKyberPublicKey)deserializeObject(serialized);
        assertArrayEquals(expectedEncoded, publicKey.getEncoded());
        return publicKey;
    }

    private static Object deserializeObject(byte[] serialized) throws Exception {
        ByteArrayInputStream byteInputStream = new ByteArrayInputStream(serialized);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream)) {
            return objectInputStream.readObject();
        }
    }

    private static byte[] serializeUnshared(BCKyberPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeUnshared(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCKyberPublicKey deserializeUnshared(byte[] serialized) throws Exception {
        ByteArrayInputStream byteInputStream = new ByteArrayInputStream(serialized);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream)) {
            return (BCKyberPublicKey)objectInputStream.readUnshared();
        }
    }

    private static final class PublicKeyHolder implements Serializable {
        private static final long serialVersionUID = 1L;

        private final PublicKey publicKey;

        private PublicKeyHolder(PublicKey publicKey) {
            this.publicKey = publicKey;
        }
    }
}

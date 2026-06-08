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

import org.bouncycastle.pqc.crypto.falcon.FalconParameters;
import org.bouncycastle.pqc.crypto.falcon.FalconPrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.falcon.BCFalconPrivateKey;
import org.junit.jupiter.api.Test;

public class BCFalconPrivateKeyTest {
    @Test
    void javaSerializationPreservesFalconPrivateKeyEncoding() throws Exception {
        BCFalconPrivateKey privateKey = createPrivateKey();

        byte[] serialized = serialize(privateKey);
        BCFalconPrivateKey restored = deserialize(serialized);

        assertPrivateKeyRoundTrip(privateKey, restored);
    }

    @Test
    void repeatedJavaSerializationPreservesRestoredFalconPrivateKeyEncoding() throws Exception {
        BCFalconPrivateKey privateKey = createPrivateKey();
        BCFalconPrivateKey firstRestored = deserialize(serialize(privateKey));

        BCFalconPrivateKey secondRestored = deserialize(serialize(firstRestored));

        assertPrivateKeyRoundTrip(privateKey, secondRestored);
    }

    private static void assertPrivateKeyRoundTrip(
            BCFalconPrivateKey privateKey,
            BCFalconPrivateKey restored) {
        assertEquals("FALCON-512", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(privateKey.getParameterSpec().getName(), restored.getParameterSpec().getName());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertArrayEquals(privateKey.getPublicKey().getEncoded(), restored.getPublicKey().getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCFalconPrivateKey createPrivateKey() {
        return new BCFalconPrivateKey(new FalconPrivateKeyParameters(
                FalconParameters.falcon_512,
                sequence(64, 1),
                sequence(64, 65),
                sequence(64, 129),
                sequence(128, 193)));
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCFalconPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCFalconPrivateKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (BCFalconPrivateKey)objectInputStream.readObject();
        }
    }
}

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

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.pqc.crypto.newhope.NHPrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.newhope.BCNHPrivateKey;
import org.junit.jupiter.api.Test;

public class BCNHPrivateKeyTest {
    @Test
    void javaSerializationPreservesNewHopePrivateKeyEncoding() throws Exception {
        BCNHPrivateKey privateKey = createPrivateKey();

        assertSerializationRoundTrip(privateKey);
    }

    @Test
    void javaSerializationPreservesDecodedNewHopePrivateKeyEncoding() throws Exception {
        BCNHPrivateKey generatedKey = createPrivateKey();
        PrivateKeyInfo keyInfo = PrivateKeyInfo.getInstance(generatedKey.getEncoded());
        BCNHPrivateKey decodedKey = new BCNHPrivateKey(keyInfo);

        assertSerializationRoundTrip(decodedKey);
    }

    private static void assertSerializationRoundTrip(BCNHPrivateKey privateKey) throws Exception {
        byte[] serialized = serialize(privateKey);
        BCNHPrivateKey restored = deserialize(serialized, privateKey.getEncoded());

        assertEquals("NH", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertArrayEquals(privateKey.getSecretData(), restored.getSecretData());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCNHPrivateKey createPrivateKey() {
        return new BCNHPrivateKey(new NHPrivateKeyParameters(sequence(1024, 1)));
    }

    private static short[] sequence(int length, int firstValue) {
        short[] values = new short[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (short)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCNHPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCNHPrivateKey deserialize(
            byte[] serialized,
            byte[] expectedEncoded) throws Exception {
        ByteArrayInputStream byteInputStream = new ByteArrayInputStream(serialized);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream)) {
            BCNHPrivateKey privateKey = (BCNHPrivateKey)objectInputStream.readObject();
            assertArrayEquals(expectedEncoded, privateKey.getEncoded());
            return privateKey;
        }
    }
}

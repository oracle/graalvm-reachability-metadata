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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.bouncycastle.pqc.crypto.rainbow.RainbowParameters;
import org.bouncycastle.pqc.crypto.rainbow.RainbowPrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.rainbow.BCRainbowPrivateKey;
import org.junit.jupiter.api.Test;

public class BCRainbowPrivateKeyTest {
    private static final int V1 = 68;
    private static final int O1 = 32;
    private static final int O2 = 48;
    private static final int N = V1 + O1 + O2;
    private static final int M = O1 + O2;
    private static final int SK_SEED_LENGTH = 32;

    @Test
    void javaSerializationPreservesRainbowPrivateKeyEncoding() throws Exception {
        BCRainbowPrivateKey privateKey = createPrivateKey();

        byte[] serialized = serialize(privateKey);
        BCRainbowPrivateKey restored = deserialize(serialized);

        assertPrivateKeyRoundTrip(privateKey, restored);
    }

    @Test
    void repeatedJavaSerializationPreservesRestoredRainbowPrivateKeyEncoding() throws Exception {
        BCRainbowPrivateKey privateKey = createPrivateKey();
        BCRainbowPrivateKey firstRestored = deserialize(serialize(privateKey));

        BCRainbowPrivateKey secondRestored = deserialize(serialize(firstRestored));

        assertPrivateKeyRoundTrip(privateKey, secondRestored);
    }

    private static void assertPrivateKeyRoundTrip(
            BCRainbowPrivateKey privateKey,
            BCRainbowPrivateKey restored) {
        assertEquals("RAINBOW-III-CLASSIC", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(privateKey.getParameterSpec().getName(), restored.getParameterSpec().getName());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertArrayEquals(privateKey.getPublicKey().getEncoded(), restored.getPublicKey().getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCRainbowPrivateKey createPrivateKey() {
        return new BCRainbowPrivateKey(new RainbowPrivateKeyParameters(
                RainbowParameters.rainbowIIIclassic,
                sequence(classicPrivateKeyEncodingLength() + classicPublicKeyEncodingLength(), 1)));
    }

    private static int classicPrivateKeyEncodingLength() {
        return SK_SEED_LENGTH
                + O1 * O2
                + V1 * O1
                + V1 * O2
                + O1 * O2
                + triangularEncodingLength(O1, V1)
                + O1 * V1 * O1
                + triangularEncodingLength(O2, V1)
                + O2 * V1 * O1
                + O2 * V1 * O2
                + triangularEncodingLength(O2, O1)
                + O2 * O1 * O2;
    }

    private static int classicPublicKeyEncodingLength() {
        return triangularEncodingLength(M, N);
    }

    private static int triangularEncodingLength(int dimension, int rows) {
        return dimension * rows * (rows + 1) / 2;
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCRainbowPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedPrivateKeyReplacingObjectOutputStream objectOutputStream =
                new EncodedPrivateKeyReplacingObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
            assertEquals(1, objectOutputStream.replacedEncodedKeys);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCRainbowPrivateKey deserialize(byte[] serialized) throws Exception {
        try (EncodedPrivateKeyResolvingObjectInputStream objectInputStream =
                new EncodedPrivateKeyResolvingObjectInputStream(new ByteArrayInputStream(serialized))) {
            BCRainbowPrivateKey restored = (BCRainbowPrivateKey)objectInputStream.readObject();
            assertEquals(1, objectInputStream.resolvedEncodedKeys);
            return restored;
        }
    }

    private static final class EncodedPrivateKeyReplacingObjectOutputStream extends ObjectOutputStream {
        private int replacedEncodedKeys;

        private EncodedPrivateKeyReplacingObjectOutputStream(ByteArrayOutputStream outputStream)
                throws IOException {
            super(outputStream);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (replacedEncodedKeys == 0 && object instanceof byte[]) {
                replacedEncodedKeys++;
                return new EncodedPrivateKey((byte[])object);
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedPrivateKeyResolvingObjectInputStream extends ObjectInputStream {
        private int resolvedEncodedKeys;

        private EncodedPrivateKeyResolvingObjectInputStream(ByteArrayInputStream inputStream)
                throws IOException {
            super(inputStream);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (object instanceof EncodedPrivateKey) {
                resolvedEncodedKeys++;
                return ((EncodedPrivateKey)object).encoded;
            }
            return super.resolveObject(object);
        }
    }

    private static final class EncodedPrivateKey implements Serializable {
        private static final long serialVersionUID = 1L;

        private final byte[] encoded;

        private EncodedPrivateKey(byte[] encoded) {
            this.encoded = encoded.clone();
        }
    }
}

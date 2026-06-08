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

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.pqc.crypto.rainbow.RainbowParameters;
import org.bouncycastle.pqc.crypto.rainbow.RainbowPublicKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.rainbow.BCRainbowPublicKey;
import org.junit.jupiter.api.Test;

public class BCRainbowPublicKeyTest {
    private static final int V1 = 68;
    private static final int O1 = 32;
    private static final int O2 = 48;
    private static final int N = V1 + O1 + O2;
    private static final int M = O1 + O2;

    @Test
    void javaSerializationPreservesRainbowPublicKeyEncoding() throws Exception {
        BCRainbowPublicKey publicKey = createPublicKey();

        byte[] serialized = serialize(publicKey);
        BCRainbowPublicKey restored = deserialize(serialized);

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    @Test
    void repeatedJavaSerializationPreservesRestoredRainbowPublicKeyEncoding() throws Exception {
        BCRainbowPublicKey publicKey = createPublicKey();
        BCRainbowPublicKey firstRestored = deserialize(serialize(publicKey));

        BCRainbowPublicKey secondRestored = deserialize(serialize(firstRestored));

        assertPublicKeyRoundTrip(publicKey, secondRestored);
    }

    private static void assertPublicKeyRoundTrip(
            BCRainbowPublicKey publicKey,
            BCRainbowPublicKey restored) {
        assertEquals("RAINBOW-III-CLASSIC", publicKey.getAlgorithm());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("X.509", restored.getFormat());
        assertEquals(publicKey.getParameterSpec().getName(), restored.getParameterSpec().getName());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
    }

    private static BCRainbowPublicKey createPublicKey() {
        return new BCRainbowPublicKey(new RainbowPublicKeyParameters(
                RainbowParameters.rainbowIIIclassic,
                sequence(classicPublicKeyEncodingLength(), 1)));
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

    private static byte[] serialize(BCRainbowPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedPublicKeyReplacingObjectOutputStream objectOutputStream =
                new EncodedPublicKeyReplacingObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCRainbowPublicKey deserialize(byte[] serialized) throws Exception {
        try (EncodedPublicKeyResolvingObjectInputStream objectInputStream =
                new EncodedPublicKeyResolvingObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (BCRainbowPublicKey)objectInputStream.readObject();
        }
    }

    private static final class EncodedPublicKeyReplacingObjectOutputStream extends ObjectOutputStream {
        private boolean replacedEncodedKey;

        private EncodedPublicKeyReplacingObjectOutputStream(ByteArrayOutputStream outputStream)
                throws IOException {
            super(outputStream);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (!replacedEncodedKey && object instanceof byte[]) {
                replacedEncodedKey = true;
                return new BCRainbowPublicKey(SubjectPublicKeyInfo.getInstance(object));
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedPublicKeyResolvingObjectInputStream extends ObjectInputStream {
        private boolean resolvedEncodedKey;

        private EncodedPublicKeyResolvingObjectInputStream(ByteArrayInputStream inputStream)
                throws IOException {
            super(inputStream);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (!resolvedEncodedKey && object instanceof BCRainbowPublicKey) {
                resolvedEncodedKey = true;
                return ((BCRainbowPublicKey)object).getEncoded();
            }
            return super.resolveObject(object);
        }
    }
}

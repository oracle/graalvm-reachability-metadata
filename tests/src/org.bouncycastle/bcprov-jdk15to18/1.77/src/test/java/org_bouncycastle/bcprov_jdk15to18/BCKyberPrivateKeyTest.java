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
import java.util.Arrays;

import org.bouncycastle.pqc.crypto.crystals.kyber.KyberParameters;
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberPrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.kyber.BCKyberPrivateKey;
import org.junit.jupiter.api.Test;

public class BCKyberPrivateKeyTest {
    @Test
    void javaSerializationPreservesKyberPrivateKeyEncoding() throws Exception {
        BCKyberPrivateKey privateKey = createPrivateKey();

        byte[] serialized = serialize(privateKey);
        BCKyberPrivateKey restored = deserialize(serialized, privateKey.getEncoded());

        assertEquals("KYBER512", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(
                privateKey.getParameterSpec().getName(),
                restored.getParameterSpec().getName());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertArrayEquals(
                privateKey.getPublicKey().getEncoded(),
                restored.getPublicKey().getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCKyberPrivateKey createPrivateKey() {
        return new BCKyberPrivateKey(new KyberPrivateKeyParameters(
                KyberParameters.kyber512,
                sequence(768, 1),
                sequence(32, 33),
                sequence(32, 65),
                sequence(768, 97),
                sequence(32, 129)));
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCKyberPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedKeyOutputStream objectOutputStream =
                new EncodedKeyOutputStream(byteOutputStream, privateKey.getEncoded())) {
            objectOutputStream.writeObject(privateKey);
            assertEquals(1, objectOutputStream.observedEncodedKeys);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCKyberPrivateKey deserialize(
            byte[] serialized,
            byte[] expectedEncoded) throws Exception {
        ByteArrayInputStream byteInputStream = new ByteArrayInputStream(serialized);
        try (EncodedKeyInputStream objectInputStream =
                new EncodedKeyInputStream(byteInputStream, expectedEncoded)) {
            BCKyberPrivateKey privateKey = (BCKyberPrivateKey)objectInputStream.readObject();
            assertEquals(1, objectInputStream.observedEncodedKeys);
            return privateKey;
        }
    }

    private static final class EncodedKeyOutputStream extends ObjectOutputStream {
        private final byte[] expectedEncoded;
        private int observedEncodedKeys;

        private EncodedKeyOutputStream(
                ByteArrayOutputStream outputStream,
                byte[] expectedEncoded) throws IOException {
            super(outputStream);
            this.expectedEncoded = expectedEncoded.clone();
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (object instanceof byte[] && Arrays.equals(expectedEncoded, (byte[])object)) {
                observedEncodedKeys++;
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedKeyInputStream extends ObjectInputStream {
        private final byte[] expectedEncoded;
        private int observedEncodedKeys;

        private EncodedKeyInputStream(
                ByteArrayInputStream inputStream,
                byte[] expectedEncoded) throws IOException {
            super(inputStream);
            this.expectedEncoded = expectedEncoded.clone();
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (object instanceof byte[] && Arrays.equals(expectedEncoded, (byte[])object)) {
                observedEncodedKeys++;
            }
            return super.resolveObject(object);
        }
    }
}

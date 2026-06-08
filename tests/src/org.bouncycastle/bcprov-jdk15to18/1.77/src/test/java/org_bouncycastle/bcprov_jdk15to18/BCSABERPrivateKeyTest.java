/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import org.bouncycastle.pqc.crypto.saber.SABERParameters;
import org.bouncycastle.pqc.crypto.saber.SABERPrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.saber.BCSABERPrivateKey;
import org.junit.jupiter.api.Test;

public class BCSABERPrivateKeyTest {
    @Test
    void javaSerializationPreservesSaberPrivateKeyEncoding() throws Exception {
        BCSABERPrivateKey privateKey = createPrivateKey();

        byte[] expectedEncoding = privateKey.getEncoded();
        byte[] serialized = serialize(privateKey, expectedEncoding);
        BCSABERPrivateKey restored = deserialize(serialized, expectedEncoding);

        assertEquals("SABER", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCSABERPrivateKey createPrivateKey() {
        return new BCSABERPrivateKey(new SABERPrivateKeyParameters(
                SABERParameters.lightsaberkem128r3,
                sequence(992, 1)));
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(
            BCSABERPrivateKey privateKey,
            byte[] expectedEncoding) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedKeyOutputStream objectOutputStream =
                new EncodedKeyOutputStream(byteOutputStream, expectedEncoding)) {
            objectOutputStream.writeObject(privateKey);
            assertEquals(1, objectOutputStream.observedEncodedKeys);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCSABERPrivateKey deserialize(
            byte[] serialized,
            byte[] expectedEncoding) throws Exception {
        try (EncodedKeyInputStream objectInputStream = new EncodedKeyInputStream(
                new ByteArrayInputStream(serialized),
                expectedEncoding)) {
            BCSABERPrivateKey privateKey = assertInstanceOf(
                    BCSABERPrivateKey.class,
                    objectInputStream.readObject());
            assertEquals(1, objectInputStream.observedEncodedKeys);
            return privateKey;
        }
    }

    private static final class EncodedKeyOutputStream extends ObjectOutputStream {
        private final byte[] expectedEncoding;
        private int observedEncodedKeys;

        private EncodedKeyOutputStream(
                ByteArrayOutputStream outputStream,
                byte[] expectedEncoding) throws IOException {
            super(outputStream);
            this.expectedEncoding = expectedEncoding.clone();
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (object instanceof byte[] && Arrays.equals(expectedEncoding, (byte[])object)) {
                observedEncodedKeys++;
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedKeyInputStream extends ObjectInputStream {
        private final byte[] expectedEncoding;
        private int observedEncodedKeys;

        private EncodedKeyInputStream(
                ByteArrayInputStream inputStream,
                byte[] expectedEncoding) throws IOException {
            super(inputStream);
            this.expectedEncoding = expectedEncoding.clone();
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (object instanceof byte[] && Arrays.equals(expectedEncoding, (byte[])object)) {
                observedEncodedKeys++;
            }
            return super.resolveObject(object);
        }
    }
}

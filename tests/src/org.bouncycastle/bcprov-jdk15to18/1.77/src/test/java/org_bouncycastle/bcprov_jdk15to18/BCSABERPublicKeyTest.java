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
import java.io.Serializable;
import java.util.Arrays;
import java.util.Base64;

import org.bouncycastle.pqc.crypto.saber.SABERParameters;
import org.bouncycastle.pqc.crypto.saber.SABERPublicKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.saber.BCSABERPublicKey;
import org.junit.jupiter.api.Test;

public class BCSABERPublicKeyTest {
    @Test
    void javaSerializationPreservesSaberPublicKeyEncoding() throws Exception {
        BCSABERPublicKey publicKey = createPublicKey();

        byte[] expectedEncoding = publicKey.getEncoded();
        byte[] serialized = serialize(publicKey, expectedEncoding);
        BCSABERPublicKey restored = deserialize(serialized, expectedEncoding);

        assertEquals("SABER", publicKey.getAlgorithm());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("X.509", restored.getFormat());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
    }

    private static BCSABERPublicKey createPublicKey() {
        return new BCSABERPublicKey(new SABERPublicKeyParameters(
                SABERParameters.lightsaberkem128r3,
                sequence(672, 1)));
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(
            BCSABERPublicKey publicKey,
            byte[] expectedEncoding) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedKeyOutputStream objectOutputStream =
                new EncodedKeyOutputStream(byteOutputStream, expectedEncoding)) {
            objectOutputStream.writeObject(publicKey);
            assertEquals(1, objectOutputStream.observedEncodedKeys);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCSABERPublicKey deserialize(
            byte[] serialized,
            byte[] expectedEncoding) throws Exception {
        try (EncodedKeyInputStream objectInputStream = new EncodedKeyInputStream(
                new ByteArrayInputStream(serialized),
                expectedEncoding)) {
            BCSABERPublicKey publicKey = assertInstanceOf(
                    BCSABERPublicKey.class,
                    objectInputStream.readObject());
            assertEquals(1, objectInputStream.observedEncodedKeys);
            return publicKey;
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
                return new SerializedEncodedKey((byte[])object);
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
            if (object instanceof SerializedEncodedKey) {
                byte[] encoded = ((SerializedEncodedKey)object).encoded();
                if (Arrays.equals(expectedEncoding, encoded)) {
                    observedEncodedKeys++;
                }
                return encoded;
            }
            return super.resolveObject(object);
        }
    }

    private static final class SerializedEncodedKey implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String base64Encoded;

        private SerializedEncodedKey(byte[] encoded) {
            this.base64Encoded = Base64.getEncoder().encodeToString(encoded);
        }

        private byte[] encoded() {
            return Base64.getDecoder().decode(base64Encoded);
        }
    }
}

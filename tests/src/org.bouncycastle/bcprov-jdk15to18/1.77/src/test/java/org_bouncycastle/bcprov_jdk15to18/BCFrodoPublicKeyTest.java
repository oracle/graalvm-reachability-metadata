/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.pqc.crypto.frodo.FrodoParameters;
import org.bouncycastle.pqc.crypto.frodo.FrodoPublicKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.provider.frodo.BCFrodoPublicKey;
import org.junit.jupiter.api.Test;

public class BCFrodoPublicKeyTest {
    @Test
    void javaSerializationPreservesFrodoPublicKeyEncoding() throws Exception {
        BCFrodoPublicKey publicKey = createPublicKey();

        BCFrodoPublicKey restored = deserialize(serialize(publicKey));

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    @Test
    void javaSerializationPreservesFrodoPublicKeyGeneratedByKeyFactory() throws Exception {
        BCFrodoPublicKey publicKey = createPublicKey();
        BCFrodoPublicKey generatedPublicKey = generatePublicKey(publicKey.getEncoded());

        BCFrodoPublicKey restored = deserialize(serializeAsPublicKey(generatedPublicKey));

        assertPublicKeyRoundTrip(generatedPublicKey, restored);
    }

    private static void assertPublicKeyRoundTrip(
            BCFrodoPublicKey publicKey,
            BCFrodoPublicKey restored) {
        assertEquals("Frodo", publicKey.getAlgorithm());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("X.509", restored.getFormat());
        assertEquals(publicKey.getParameterSpec().getName(), restored.getParameterSpec().getName());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
    }

    private static BCFrodoPublicKey createPublicKey() {
        FrodoParameters parameters = FrodoParameters.frodokem640aes;
        byte[] publicKey = sequence(frodoKem640PublicKeySize(), 17);
        return new BCFrodoPublicKey(new FrodoPublicKeyParameters(parameters, publicKey));
    }

    private static int frodoKem640PublicKeySize() {
        int seedABytes = 16;
        int dimension = 640;
        int encodedMatrixWidth = 8;
        int bitsPerCoefficient = 15;
        return seedABytes + bitsPerCoefficient * dimension * encodedMatrixWidth / Byte.SIZE;
    }

    private static BCFrodoPublicKey generatePublicKey(byte[] encodedPublicKey) throws Exception {
        BouncyCastlePQCProvider provider = new BouncyCastlePQCProvider();
        KeyFactory keyFactory = KeyFactory.getInstance("FRODO", provider);
        PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(encodedPublicKey));
        return (BCFrodoPublicKey)publicKey;
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCFrodoPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedPublicKeyReplacingStream objectOutputStream =
                new EncodedPublicKeyReplacingStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
            assertTrue(objectOutputStream.hasReplacedEncodedKey());
        }
        return byteOutputStream.toByteArray();
    }

    private static byte[] serializeAsPublicKey(PublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedPublicKeyReplacingStream objectOutputStream =
                new EncodedPublicKeyReplacingStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
            assertTrue(objectOutputStream.hasReplacedEncodedKey());
        }
        return byteOutputStream.toByteArray();
    }

    private static BCFrodoPublicKey deserialize(byte[] serialized) throws Exception {
        try (EncodedPublicKeyResolvingStream objectInputStream =
                new EncodedPublicKeyResolvingStream(new ByteArrayInputStream(serialized))) {
            BCFrodoPublicKey publicKey = (BCFrodoPublicKey)objectInputStream.readObject();
            assertTrue(objectInputStream.hasResolvedEncodedKey());
            return publicKey;
        }
    }

    private static final class EncodedPublicKeyReplacingStream extends ObjectOutputStream {
        private boolean replacedEncodedKey;

        private EncodedPublicKeyReplacingStream(ByteArrayOutputStream outputStream)
                throws IOException {
            super(outputStream);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (!replacedEncodedKey && object instanceof byte[]) {
                replacedEncodedKey = true;
                return new EncodedPublicKey((byte[])object);
            }
            return super.replaceObject(object);
        }

        private boolean hasReplacedEncodedKey() {
            return replacedEncodedKey;
        }
    }

    private static final class EncodedPublicKeyResolvingStream extends ObjectInputStream {
        private boolean resolvedEncodedKey;

        private EncodedPublicKeyResolvingStream(ByteArrayInputStream inputStream)
                throws IOException {
            super(inputStream);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (!resolvedEncodedKey && object instanceof EncodedPublicKey) {
                resolvedEncodedKey = true;
                return ((EncodedPublicKey)object).toByteArray();
            }
            return super.resolveObject(object);
        }

        private boolean hasResolvedEncodedKey() {
            return resolvedEncodedKey;
        }
    }

    private static final class EncodedPublicKey implements Serializable {
        private static final long serialVersionUID = 1L;

        private final int[] encodedKey;

        private EncodedPublicKey(byte[] encodedKey) {
            this.encodedKey = new int[encodedKey.length];
            for (int i = 0; i < encodedKey.length; i++) {
                this.encodedKey[i] = encodedKey[i] & 0xff;
            }
        }

        private byte[] toByteArray() {
            byte[] bytes = new byte[encodedKey.length];
            for (int i = 0; i < encodedKey.length; i++) {
                bytes[i] = (byte)encodedKey[i];
            }
            return bytes;
        }
    }
}

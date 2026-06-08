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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimeParameters;
import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimePublicKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.provider.ntruprime.BCSNTRUPrimePublicKey;
import org.junit.jupiter.api.Test;

public class BCSNTRUPrimePublicKeyTest {
    @Test
    void javaSerializationPreservesSntruPrimePublicKeyEncoding() throws Exception {
        assertJavaSerializationPreservesSntruPrimePublicKeyEncoding(createPublicKey());
    }

    @Test
    void nestedJavaSerializationPreservesSntruPrimePublicKeyEncoding() throws Exception {
        BCSNTRUPrimePublicKey publicKey = createPublicKey();

        byte[] serialized = serializeWithNestedEncodedKey(publicKey);
        PublicKey restored = deserializeWithNestedEncodedKey(serialized);
        BCSNTRUPrimePublicKey restoredSntruPrimeKey = assertInstanceOf(
                BCSNTRUPrimePublicKey.class,
                restored);

        assertPublicKeyRoundTrip(publicKey, restoredSntruPrimeKey);
    }

    @Test
    void javaSerializationPreservesKeyFactorySntruPrimePublicKeyEncoding() throws Exception {
        BCSNTRUPrimePublicKey publicKey = createPublicKey();
        KeyFactory keyFactory = KeyFactory.getInstance(
                "SNTRUPRIME",
                new BouncyCastlePQCProvider());
        PublicKey decodedKey = keyFactory.generatePublic(
                new X509EncodedKeySpec(publicKey.getEncoded()));
        BCSNTRUPrimePublicKey decodedSntruPrimeKey = assertInstanceOf(
                BCSNTRUPrimePublicKey.class,
                decodedKey);

        assertJavaSerializationPreservesSntruPrimePublicKeyEncoding(decodedSntruPrimeKey);
    }

    private static void assertJavaSerializationPreservesSntruPrimePublicKeyEncoding(
            BCSNTRUPrimePublicKey publicKey)
            throws Exception {
        byte[] serialized = serialize(publicKey);
        PublicKey restored = deserialize(serialized);
        BCSNTRUPrimePublicKey restoredSntruPrimeKey = assertInstanceOf(
                BCSNTRUPrimePublicKey.class,
                restored);

        assertPublicKeyRoundTrip(publicKey, restoredSntruPrimeKey);
    }

    private static void assertPublicKeyRoundTrip(
            BCSNTRUPrimePublicKey publicKey,
            BCSNTRUPrimePublicKey restored) {
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("X.509", restored.getFormat());
        assertEquals(
                publicKey.getParameterSpec().getName(),
                restored.getParameterSpec().getName());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
    }

    private static BCSNTRUPrimePublicKey createPublicKey() {
        SNTRUPrimeParameters parameters = SNTRUPrimeParameters.sntrup653;
        SNTRUPrimePublicKeyParameters keyParameters = new SNTRUPrimePublicKeyParameters(
                parameters,
                sequence(parameters.getPublicKeyBytes(), 1));
        return new BCSNTRUPrimePublicKey(keyParameters);
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(PublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static PublicKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (PublicKey)objectInputStream.readObject();
        }
    }

    private static byte[] serializeWithNestedEncodedKey(PublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedKeyReplacingStream objectOutputStream =
                new EncodedKeyReplacingStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
            assertTrue(objectOutputStream.hasReplacedEncodedKey());
        }
        return byteOutputStream.toByteArray();
    }

    private static PublicKey deserializeWithNestedEncodedKey(byte[] serialized)
            throws Exception {
        try (EncodedKeyResolvingStream objectInputStream = new EncodedKeyResolvingStream(
                new ByteArrayInputStream(serialized))) {
            PublicKey publicKey = (PublicKey)objectInputStream.readObject();
            assertTrue(objectInputStream.hasResolvedEncodedKey());
            return publicKey;
        }
    }

    private static final class EncodedKeyReplacingStream extends ObjectOutputStream {
        private boolean replacedEncodedKey;

        private EncodedKeyReplacingStream(ByteArrayOutputStream outputStream)
                throws Exception {
            super(outputStream);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) {
            if (!replacedEncodedKey && object instanceof byte[]) {
                replacedEncodedKey = true;
                return new EncodedKey((byte[])object);
            }
            return object;
        }

        private boolean hasReplacedEncodedKey() {
            return replacedEncodedKey;
        }
    }

    private static final class EncodedKeyResolvingStream extends ObjectInputStream {
        private boolean resolvedEncodedKey;

        private EncodedKeyResolvingStream(ByteArrayInputStream inputStream)
                throws Exception {
            super(inputStream);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) {
            if (!resolvedEncodedKey && object instanceof EncodedKey) {
                resolvedEncodedKey = true;
                return ((EncodedKey)object).toByteArray();
            }
            return object;
        }

        private boolean hasResolvedEncodedKey() {
            return resolvedEncodedKey;
        }
    }

    private static final class EncodedKey implements Serializable {
        private static final long serialVersionUID = 1L;

        private final int[] encodedKey;

        private EncodedKey(byte[] encodedKey) {
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


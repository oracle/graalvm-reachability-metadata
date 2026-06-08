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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.PublicKey;

import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.pqc.crypto.ntruprime.NTRULPRimeParameters;
import org.bouncycastle.pqc.crypto.ntruprime.NTRULPRimePublicKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.ntruprime.BCNTRULPRimePublicKey;
import org.junit.jupiter.api.Test;

public class BCNTRULPRimePublicKeyTest {
    @Test
    void javaSerializationPreservesNtruLPrimePublicKeyEncoding() throws Exception {
        BCNTRULPRimePublicKey publicKey = createPublicKey();

        byte[] encodedPublicKey = publicKey.getEncoded();
        byte[] serialized = serialize(publicKey);
        PublicKey restored = deserialize(serialized, encodedPublicKey);
        BCNTRULPRimePublicKey restoredNtruLPrimeKey = assertInstanceOf(
                BCNTRULPRimePublicKey.class,
                restored);

        assertEquals("NTRULPRime", publicKey.getAlgorithm());
        assertEquals(publicKey.getAlgorithm(), restoredNtruLPrimeKey.getAlgorithm());
        assertEquals("X.509", restoredNtruLPrimeKey.getFormat());
        assertEquals(
                publicKey.getParameterSpec().getName(),
                restoredNtruLPrimeKey.getParameterSpec().getName());
        assertArrayEquals(publicKey.getEncoded(), restoredNtruLPrimeKey.getEncoded());
        assertEquals(publicKey, restoredNtruLPrimeKey);
        assertEquals(publicKey.hashCode(), restoredNtruLPrimeKey.hashCode());
    }

    private static BCNTRULPRimePublicKey createPublicKey() {
        NTRULPRimeParameters parameters = NTRULPRimeParameters.ntrulpr653;
        NTRULPRimePublicKeyParameters keyParameters = new NTRULPRimePublicKeyParameters(
                parameters,
                sequence(parameters.getPublicKeyBytes(), 1));
        return new BCNTRULPRimePublicKey(keyParameters);
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
        try (EncodedPublicKeyReplacingStream objectOutputStream = new EncodedPublicKeyReplacingStream(
                byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
            assertTrue(objectOutputStream.replacedEncodedKey());
        }
        return byteOutputStream.toByteArray();
    }

    private static PublicKey deserialize(byte[] serialized, byte[] encodedPublicKey)
            throws Exception {
        try (EncodedPublicKeyResolvingStream objectInputStream = new EncodedPublicKeyResolvingStream(
                new ByteArrayInputStream(serialized),
                encodedPublicKey)) {
            PublicKey publicKey = (PublicKey)objectInputStream.readObject();
            assertTrue(objectInputStream.resolvedEncodedKey());
            return publicKey;
        }
    }

    private static CryptoException createReplacementForEncodedKey() {
        return new CryptoException("encoded public key placeholder");
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
                return createReplacementForEncodedKey();
            }
            return super.replaceObject(object);
        }

        private boolean replacedEncodedKey() {
            return replacedEncodedKey;
        }
    }

    private static final class EncodedPublicKeyResolvingStream extends ObjectInputStream {
        private final byte[] encodedPublicKey;

        private boolean resolvedEncodedKey;

        private EncodedPublicKeyResolvingStream(
                ByteArrayInputStream inputStream,
                byte[] encodedPublicKey)
                throws IOException {
            super(inputStream);
            this.encodedPublicKey = encodedPublicKey.clone();
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (!resolvedEncodedKey && object instanceof CryptoException) {
                resolvedEncodedKey = true;
                return encodedPublicKey.clone();
            }
            return super.resolveObject(object);
        }

        private boolean resolvedEncodedKey() {
            return resolvedEncodedKey;
        }
    }
}

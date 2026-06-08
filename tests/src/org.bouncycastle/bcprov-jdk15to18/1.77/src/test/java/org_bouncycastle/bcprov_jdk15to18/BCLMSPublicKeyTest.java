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
import java.security.KeyPair;
import java.security.SecureRandom;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.pqc.crypto.lms.LMOtsParameters;
import org.bouncycastle.pqc.crypto.lms.LMSPublicKeyParameters;
import org.bouncycastle.pqc.crypto.lms.LMSigParameters;
import org.bouncycastle.pqc.jcajce.provider.lms.BCLMSPublicKey;
import org.bouncycastle.pqc.jcajce.provider.lms.LMSKeyPairGeneratorSpi;
import org.bouncycastle.pqc.jcajce.spec.LMSKeyGenParameterSpec;
import org.junit.jupiter.api.Test;

public class BCLMSPublicKeyTest {
    @Test
    void javaSerializationPreservesLmsPublicKeyEncoding() throws Exception {
        BCLMSPublicKey publicKey = createPublicKey();

        byte[] serialized = serialize(publicKey);
        BCLMSPublicKey restored = deserialize(serialized);

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    @Test
    void repeatedJavaSerializationPreservesRestoredLmsPublicKeyEncoding() throws Exception {
        BCLMSPublicKey publicKey = createPublicKey();
        BCLMSPublicKey firstRestored = deserialize(serialize(publicKey));

        BCLMSPublicKey secondRestored = deserialize(serialize(firstRestored));

        assertPublicKeyRoundTrip(publicKey, secondRestored);
    }

    @Test
    void javaSerializationPreservesGeneratedLmsPublicKeyEncoding() throws Exception {
        BCLMSPublicKey publicKey = generatePublicKey();

        BCLMSPublicKey restored = deserialize(serialize(publicKey));

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    private static void assertPublicKeyRoundTrip(
            BCLMSPublicKey publicKey,
            BCLMSPublicKey restored) {
        assertEquals("LMS", publicKey.getAlgorithm());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("X.509", restored.getFormat());
        assertEquals(1, restored.getLevels());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
    }

    private static BCLMSPublicKey createPublicKey() {
        LMSigParameters signatureParameters = LMSigParameters.lms_sha256_n32_h5;
        LMSPublicKeyParameters keyParameters = new LMSPublicKeyParameters(
                signatureParameters,
                LMOtsParameters.sha256_n32_w8,
                sequence(signatureParameters.getM(), 1),
                sequence(16, 65));
        return new BCLMSPublicKey(keyParameters);
    }

    private static BCLMSPublicKey generatePublicKey() throws Exception {
        LMSKeyPairGeneratorSpi keyPairGenerator = new LMSKeyPairGeneratorSpi();
        keyPairGenerator.initialize(
                new LMSKeyGenParameterSpec(
                        LMSigParameters.lms_sha256_n32_h5,
                        LMOtsParameters.sha256_n32_w8),
                new SecureRandom(new byte[] {1, 2, 3, 4}));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return (BCLMSPublicKey)keyPair.getPublic();
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCLMSPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream =
                new EncodedPublicKeyReplacingObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCLMSPublicKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new EncodedPublicKeyResolvingObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (BCLMSPublicKey)objectInputStream.readObject();
        }
    }

    private static final class EncodedPublicKeyReplacingObjectOutputStream
            extends ObjectOutputStream {
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
                return new BCLMSPublicKey(SubjectPublicKeyInfo.getInstance((byte[])object));
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedPublicKeyResolvingObjectInputStream
            extends ObjectInputStream {
        private boolean resolvedNestedKey;

        private EncodedPublicKeyResolvingObjectInputStream(ByteArrayInputStream inputStream)
                throws IOException {
            super(inputStream);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (!resolvedNestedKey && object instanceof BCLMSPublicKey) {
                resolvedNestedKey = true;
                return ((BCLMSPublicKey)object).getEncoded();
            }
            return super.resolveObject(object);
        }
    }
}

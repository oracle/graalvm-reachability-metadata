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

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.pqc.crypto.lms.LMOtsParameters;
import org.bouncycastle.pqc.crypto.lms.LMSPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.lms.LMSigParameters;
import org.bouncycastle.pqc.jcajce.provider.lms.BCLMSPrivateKey;
import org.junit.jupiter.api.Test;

public class BCLMSPrivateKeyTest {
    @Test
    void javaSerializationPreservesLmsPrivateKeyEncoding() throws Exception {
        BCLMSPrivateKey privateKey = createPrivateKey();

        byte[] serialized = serialize(privateKey);
        BCLMSPrivateKey restored = deserialize(serialized);

        assertPrivateKeyRoundTrip(privateKey, restored);
    }

    @Test
    void repeatedJavaSerializationPreservesRestoredLmsPrivateKeyEncoding() throws Exception {
        BCLMSPrivateKey privateKey = createPrivateKey();
        BCLMSPrivateKey firstRestored = deserialize(serialize(privateKey));

        BCLMSPrivateKey secondRestored = deserialize(serialize(firstRestored));

        assertPrivateKeyRoundTrip(privateKey, secondRestored);
    }

    private static void assertPrivateKeyRoundTrip(
            BCLMSPrivateKey privateKey,
            BCLMSPrivateKey restored) {
        assertEquals("LMS", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(1, restored.getLevels());
        assertEquals(privateKey.getIndex(), restored.getIndex());
        assertEquals(privateKey.getUsagesRemaining(), restored.getUsagesRemaining());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCLMSPrivateKey createPrivateKey() {
        LMSigParameters signatureParameters = LMSigParameters.lms_sha256_n32_h5;
        LMSPrivateKeyParameters keyParameters = new LMSPrivateKeyParameters(
                signatureParameters,
                LMOtsParameters.sha256_n32_w8,
                0,
                sequence(16, 1),
                1 << signatureParameters.getH(),
                sequence(signatureParameters.getM(), 17));
        return new BCLMSPrivateKey(keyParameters);
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCLMSPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new EncodedPrivateKeyReplacingObjectOutputStream(
                byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCLMSPrivateKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new EncodedPrivateKeyResolvingObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (BCLMSPrivateKey)objectInputStream.readObject();
        }
    }

    private static final class EncodedPrivateKeyReplacingObjectOutputStream extends ObjectOutputStream {
        private boolean replacedEncodedKey;

        private EncodedPrivateKeyReplacingObjectOutputStream(ByteArrayOutputStream outputStream)
                throws IOException {
            super(outputStream);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (!replacedEncodedKey && object instanceof byte[]) {
                replacedEncodedKey = true;
                return new BCLMSPrivateKey(PrivateKeyInfo.getInstance((byte[])object));
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedPrivateKeyResolvingObjectInputStream extends ObjectInputStream {
        private boolean resolvedNestedKey;

        private EncodedPrivateKeyResolvingObjectInputStream(ByteArrayInputStream inputStream)
                throws IOException {
            super(inputStream);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (!resolvedNestedKey && object instanceof BCLMSPrivateKey) {
                resolvedNestedKey = true;
                return ((BCLMSPrivateKey)object).getEncoded();
            }
            return super.resolveObject(object);
        }
    }
}

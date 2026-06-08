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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.Arrays;

import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.pqc.crypto.xmss.XMSSParameters;
import org.bouncycastle.pqc.crypto.xmss.XMSSPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.xmss.XMSSUtil;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.provider.xmss.BCXMSSPrivateKey;
import org.bouncycastle.pqc.jcajce.spec.XMSSParameterSpec;
import org.junit.jupiter.api.Test;

public class BCXMSSPrivateKeyTest {
    private static final int TEST_TREE_HEIGHT = 4;

    @Test
    void javaSerializationPreservesXmssPrivateKeyEncoding() throws Exception {
        BCXMSSPrivateKey privateKey = createPrivateKey();
        BCXMSSPrivateKey restored = deserialize(serialize(privateKey), privateKey.getEncoded());

        assertEquals("XMSS", restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(privateKey.getHeight(), restored.getHeight());
        assertEquals(privateKey.getTreeDigest(), restored.getTreeDigest());
        assertEquals(privateKey.getIndex(), restored.getIndex());
        assertEquals(privateKey.getUsagesRemaining(), restored.getUsagesRemaining());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    @Test
    void javaSerializationPreservesXmssPrivateKeyThroughPrivateKeyApi() throws Exception {
        BCXMSSPrivateKey privateKey = createGeneratedPrivateKey();
        PrivateKey restored = (PrivateKey)deserializeObject(serializeObject(privateKey));

        assertEquals("XMSS", restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    @Test
    void xmssUtilitySerializationPreservesXmssPrivateKey() throws Exception {
        BCXMSSPrivateKey privateKey = createPrivateKey();
        byte[] serialized = XMSSUtil.serialize(privateKey);
        BCXMSSPrivateKey restored = (BCXMSSPrivateKey)XMSSUtil.deserialize(
                serialized,
                BCXMSSPrivateKey.class);

        assertEquals("XMSS", restored.getAlgorithm());
        assertEquals(privateKey.getHeight(), restored.getHeight());
        assertEquals(privateKey.getTreeDigest(), restored.getTreeDigest());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
    }

    private static BCXMSSPrivateKey createPrivateKey() {
        XMSSParameters parameters = new XMSSParameters(TEST_TREE_HEIGHT, new SHA256Digest());
        XMSSPrivateKeyParameters.Builder keyBuilder = new XMSSPrivateKeyParameters.Builder(
                parameters);
        XMSSPrivateKeyParameters keyParameters = keyBuilder
                .withSecretKeySeed(sequence(parameters.getTreeDigestSize(), 1))
                .withSecretKeyPRF(sequence(parameters.getTreeDigestSize(), 33))
                .withPublicSeed(sequence(parameters.getTreeDigestSize(), 65))
                .withRoot(sequence(parameters.getTreeDigestSize(), 97))
                .build();
        return new BCXMSSPrivateKey(NISTObjectIdentifiers.id_sha256, keyParameters);
    }

    private static BCXMSSPrivateKey createGeneratedPrivateKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                "XMSS",
                new BouncyCastlePQCProvider());
        keyPairGenerator.initialize(
                new XMSSParameterSpec(TEST_TREE_HEIGHT, XMSSParameterSpec.SHA256),
                new SecureRandom(new byte[] {1, 2, 3, 4}));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return (BCXMSSPrivateKey)keyPair.getPrivate();
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCXMSSPrivateKey privateKey) throws IOException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedKeyOutputStream objectOutputStream = new EncodedKeyOutputStream(
                byteOutputStream,
                privateKey.getEncoded())) {
            objectOutputStream.writeObject(privateKey);
            assertTrue(objectOutputStream.replacedEncodedKey);
            assertTrue(objectOutputStream.observedPrivateWriteObject);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCXMSSPrivateKey deserialize(byte[] serialized, byte[] expectedEncoded)
            throws IOException, ClassNotFoundException {
        try (EncodedKeyInputStream objectInputStream = new EncodedKeyInputStream(
                new ByteArrayInputStream(serialized),
                expectedEncoded)) {
            BCXMSSPrivateKey privateKey = (BCXMSSPrivateKey)objectInputStream.readObject();
            assertTrue(objectInputStream.resolvedEncodedKey);
            assertTrue(objectInputStream.observedPrivateReadObject);
            return privateKey;
        }
    }

    private static byte[] serializeObject(Object object) throws IOException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(object);
        }
        return byteOutputStream.toByteArray();
    }

    private static Object deserializeObject(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return objectInputStream.readObject();
        }
    }

    private static boolean containsStackFrame(String methodName) {
        for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
            if (BCXMSSPrivateKey.class.getName().equals(stackTraceElement.getClassName())
                    && methodName.equals(stackTraceElement.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    private static final class EncodedKeyOutputStream extends ObjectOutputStream {
        private final byte[] expectedEncoded;
        private boolean replacedEncodedKey;
        private boolean observedPrivateWriteObject;

        private EncodedKeyOutputStream(ByteArrayOutputStream outputStream, byte[] expectedEncoded)
                throws IOException {
            super(outputStream);
            this.expectedEncoded = expectedEncoded.clone();
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (!replacedEncodedKey
                    && object instanceof byte[]
                    && Arrays.equals(expectedEncoded, (byte[])object)) {
                replacedEncodedKey = true;
                observedPrivateWriteObject = containsStackFrame("writeObject");
                return ((byte[])object).clone();
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedKeyInputStream extends ObjectInputStream {
        private final byte[] expectedEncoded;
        private boolean resolvedEncodedKey;
        private boolean observedPrivateReadObject;

        private EncodedKeyInputStream(ByteArrayInputStream inputStream, byte[] expectedEncoded)
                throws IOException {
            super(inputStream);
            this.expectedEncoded = expectedEncoded.clone();
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (!resolvedEncodedKey
                    && object instanceof byte[]
                    && Arrays.equals(expectedEncoded, (byte[])object)) {
                resolvedEncodedKey = true;
                observedPrivateReadObject = containsStackFrame("readObject");
                return ((byte[])object).clone();
            }
            return super.resolveObject(object);
        }
    }
}

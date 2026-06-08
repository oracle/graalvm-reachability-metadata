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
import java.security.PrivateKey;
import java.util.Arrays;

import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.pqc.crypto.xmss.XMSSMTParameters;
import org.bouncycastle.pqc.crypto.xmss.XMSSMTPrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.xmss.BCXMSSMTPrivateKey;
import org.junit.jupiter.api.Test;

public class BCXMSSMTPrivateKeyTest {
    private static final int TEST_TREE_HEIGHT = 4;
    private static final int TEST_TREE_LAYERS = 2;

    @Test
    void javaSerializationPreservesXmssMtPrivateKeyEncoding() throws Exception {
        BCXMSSMTPrivateKey privateKey = createPrivateKey();
        BCXMSSMTPrivateKey restored = deserialize(serialize(privateKey), privateKey.getEncoded());

        assertEquals("XMSSMT", restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(privateKey.getHeight(), restored.getHeight());
        assertEquals(privateKey.getLayers(), restored.getLayers());
        assertEquals(privateKey.getTreeDigest(), restored.getTreeDigest());
        assertEquals(privateKey.getIndex(), restored.getIndex());
        assertEquals(privateKey.getUsagesRemaining(), restored.getUsagesRemaining());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    @Test
    void javaSerializationPreservesXmssMtPrivateKeyThroughPrivateKeyApi() throws Exception {
        BCXMSSMTPrivateKey privateKey = createPrivateKey();
        PrivateKey restored = (PrivateKey)deserializeObject(serializeObject(privateKey));

        assertEquals("XMSSMT", restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCXMSSMTPrivateKey createPrivateKey() {
        XMSSMTParameters parameters = new XMSSMTParameters(
                TEST_TREE_HEIGHT,
                TEST_TREE_LAYERS,
                new SHA256Digest());
        XMSSMTPrivateKeyParameters.Builder keyBuilder = new XMSSMTPrivateKeyParameters.Builder(
                parameters);
        XMSSMTPrivateKeyParameters keyParameters = keyBuilder
                .withSecretKeySeed(sequence(parameters.getTreeDigestSize(), 1))
                .withSecretKeyPRF(sequence(parameters.getTreeDigestSize(), 33))
                .withPublicSeed(sequence(parameters.getTreeDigestSize(), 65))
                .withRoot(sequence(parameters.getTreeDigestSize(), 97))
                .build();
        return new BCXMSSMTPrivateKey(NISTObjectIdentifiers.id_sha256, keyParameters);
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCXMSSMTPrivateKey privateKey) throws IOException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedKeyOutputStream objectOutputStream = new EncodedKeyOutputStream(
                byteOutputStream,
                privateKey.getEncoded())) {
            objectOutputStream.writeObject(privateKey);
            assertTrue(objectOutputStream.replacedEncodedKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCXMSSMTPrivateKey deserialize(byte[] serialized, byte[] expectedEncoded)
            throws IOException, ClassNotFoundException {
        try (EncodedKeyInputStream objectInputStream = new EncodedKeyInputStream(
                new ByteArrayInputStream(serialized),
                expectedEncoded)) {
            BCXMSSMTPrivateKey privateKey = (BCXMSSMTPrivateKey)objectInputStream.readObject();
            assertTrue(objectInputStream.resolvedEncodedKey);
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

    private static final class EncodedKeyOutputStream extends ObjectOutputStream {
        private final byte[] expectedEncoded;
        private boolean replacedEncodedKey;

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
                return ((byte[])object).clone();
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedKeyInputStream extends ObjectInputStream {
        private final byte[] expectedEncoded;
        private boolean resolvedEncodedKey;

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
                return ((byte[])object).clone();
            }
            return super.resolveObject(object);
        }
    }
}

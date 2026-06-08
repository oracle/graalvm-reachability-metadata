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
import java.security.PublicKey;
import java.security.SecureRandom;

import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.pqc.crypto.xmss.XMSSMTParameters;
import org.bouncycastle.pqc.crypto.xmss.XMSSMTPublicKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.provider.xmss.BCXMSSMTPublicKey;
import org.bouncycastle.pqc.jcajce.spec.XMSSMTParameterSpec;
import org.junit.jupiter.api.Test;

public class BCXMSSMTPublicKeyTest {
    private static final int TEST_TREE_HEIGHT = 4;
    private static final int TEST_TREE_LAYERS = 2;

    @Test
    void javaSerializationPreservesXmssMtPublicKeyEncoding() throws Exception {
        BCXMSSMTPublicKey publicKey = createPublicKey();
        BCXMSSMTPublicKey restored = deserialize(serialize(publicKey));

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    @Test
    void javaSerializationPreservesXmssMtPublicKeyThroughPublicKeyApi() throws Exception {
        PublicKey publicKey = createPublicKey();
        PublicKey restored = (PublicKey)deserializeObject(serializeObject(publicKey));

        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
    }

    @Test
    void javaSerializationPreservesGeneratedXmssMtPublicKeyEncoding() throws Exception {
        BCXMSSMTPublicKey publicKey = createGeneratedPublicKey();
        BCXMSSMTPublicKey restored = (BCXMSSMTPublicKey)deserializeObject(
                serializeObject(publicKey));

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    private static void assertPublicKeyRoundTrip(
            BCXMSSMTPublicKey publicKey,
            BCXMSSMTPublicKey restored) {
        assertEquals("XMSSMT", restored.getAlgorithm());
        assertEquals("X.509", restored.getFormat());
        assertEquals(publicKey.getHeight(), restored.getHeight());
        assertEquals(publicKey.getLayers(), restored.getLayers());
        assertEquals(publicKey.getTreeDigest(), restored.getTreeDigest());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
    }

    private static BCXMSSMTPublicKey createPublicKey() {
        XMSSMTParameters parameters = new XMSSMTParameters(
                TEST_TREE_HEIGHT,
                TEST_TREE_LAYERS,
                new SHA256Digest());
        XMSSMTPublicKeyParameters keyParameters = new XMSSMTPublicKeyParameters.Builder(
                parameters)
                .withRoot(sequence(parameters.getTreeDigestSize(), 1))
                .withPublicSeed(sequence(parameters.getTreeDigestSize(), 33))
                .build();
        return new BCXMSSMTPublicKey(NISTObjectIdentifiers.id_sha256, keyParameters);
    }

    private static BCXMSSMTPublicKey createGeneratedPublicKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                "XMSSMT",
                new BouncyCastlePQCProvider());
        keyPairGenerator.initialize(
                new XMSSMTParameterSpec(
                        TEST_TREE_HEIGHT,
                        TEST_TREE_LAYERS,
                        XMSSMTParameterSpec.SHA256),
                new SecureRandom(new byte[] {1, 2, 3, 4}));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return (BCXMSSMTPublicKey)keyPair.getPublic();
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCXMSSMTPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedPublicKeyReplacingObjectOutputStream objectOutputStream =
                new EncodedPublicKeyReplacingObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
            assertTrue(objectOutputStream.replacedEncodedKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCXMSSMTPublicKey deserialize(byte[] serialized) throws Exception {
        try (EncodedPublicKeyResolvingObjectInputStream objectInputStream =
                new EncodedPublicKeyResolvingObjectInputStream(
                        new ByteArrayInputStream(serialized))) {
            BCXMSSMTPublicKey publicKey = (BCXMSSMTPublicKey)objectInputStream.readObject();
            assertTrue(objectInputStream.resolvedNestedKey);
            return publicKey;
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
                return new BCXMSSMTPublicKey(SubjectPublicKeyInfo.getInstance((byte[])object));
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
            if (!resolvedNestedKey && object instanceof BCXMSSMTPublicKey) {
                resolvedNestedKey = true;
                return ((BCXMSSMTPublicKey)object).getEncoded();
            }
            return super.resolveObject(object);
        }
    }
}

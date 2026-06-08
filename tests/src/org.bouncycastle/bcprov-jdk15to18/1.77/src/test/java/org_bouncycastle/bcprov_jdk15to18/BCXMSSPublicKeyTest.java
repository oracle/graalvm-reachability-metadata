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

import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.pqc.crypto.xmss.XMSSParameters;
import org.bouncycastle.pqc.crypto.xmss.XMSSPublicKeyParameters;
import org.bouncycastle.pqc.crypto.xmss.XMSSUtil;
import org.bouncycastle.pqc.jcajce.provider.xmss.BCXMSSPublicKey;
import org.junit.jupiter.api.Test;

public class BCXMSSPublicKeyTest {
    private static final int TEST_TREE_HEIGHT = 4;

    @Test
    void javaSerializationWritesXmssPublicKeyEncoding() throws Exception {
        byte[] serialized = serializeObject(createPublicKey());

        assertTrue(serialized.length > 0);
    }

    @Test
    void javaSerializationReadsXmssPublicKeyEncoding() throws Exception {
        BCXMSSPublicKey publicKey = createPublicKey();
        byte[] serialized = serializeObject(publicKey);
        BCXMSSPublicKey restored = (BCXMSSPublicKey)deserializeObject(serialized);

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    @Test
    void xmssUtilitySerializationPreservesXmssPublicKey() throws Exception {
        BCXMSSPublicKey publicKey = createPublicKey();
        byte[] serialized = XMSSUtil.serialize(publicKey);
        BCXMSSPublicKey restored = (BCXMSSPublicKey)XMSSUtil.deserialize(
                serialized,
                BCXMSSPublicKey.class);

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    private static void assertPublicKeyRoundTrip(
            BCXMSSPublicKey publicKey,
            BCXMSSPublicKey restored) {
        assertEquals("XMSS", restored.getAlgorithm());
        assertEquals("X.509", restored.getFormat());
        assertEquals(publicKey.getHeight(), restored.getHeight());
        assertEquals(publicKey.getTreeDigest(), restored.getTreeDigest());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
    }

    private static BCXMSSPublicKey createPublicKey() {
        XMSSParameters parameters = new XMSSParameters(TEST_TREE_HEIGHT, new SHA256Digest());
        XMSSPublicKeyParameters keyParameters = new XMSSPublicKeyParameters.Builder(parameters)
                .withRoot(sequence(parameters.getTreeDigestSize(), 1))
                .withPublicSeed(sequence(parameters.getTreeDigestSize(), 33))
                .build();
        return new BCXMSSPublicKey(NISTObjectIdentifiers.id_sha256, keyParameters);
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
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
}

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

import java.util.TreeMap;

import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.pqc.crypto.xmss.XMSSParameters;
import org.bouncycastle.pqc.crypto.xmss.XMSSPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.xmss.XMSSUtil;
import org.junit.jupiter.api.Test;

public class XMSSUtilTest {
    @Test
    void serializesAndDeserializesByteArray() throws Exception {
        byte[] original = new byte[] {
            0x01, 0x23, 0x45, 0x67, (byte)0x89, (byte)0xab, (byte)0xcd, (byte)0xef,
        };

        byte[] serialized = XMSSUtil.serialize(original);
        byte[] restored = (byte[])XMSSUtil.deserialize(serialized, byte[].class);

        assertArrayEquals(original, restored);
    }

    @Test
    void xmssPrivateKeyEncodingSerializesBdsState() throws Exception {
        XMSSParameters parameters = new XMSSParameters(2, NISTObjectIdentifiers.id_sha256);
        int digestSize = parameters.getTreeDigestSize();
        byte[] secretKeySeed = sequence(digestSize, 0x10);
        byte[] secretKeyPrf = sequence(digestSize, 0x30);
        byte[] publicSeed = sequence(digestSize, 0x50);
        byte[] root = sequence(digestSize, 0x70);

        XMSSPrivateKeyParameters privateKey = new XMSSPrivateKeyParameters.Builder(parameters)
            .withSecretKeySeed(secretKeySeed)
            .withSecretKeyPRF(secretKeyPrf)
            .withPublicSeed(publicSeed)
            .withRoot(root)
            .build();

        byte[] encoded = privateKey.getEncoded();

        assertTrue(encoded.length > Integer.BYTES + (4 * digestSize));
    }

    @Test
    void serializesAndDeserializesAllowedTreeMapComponents() throws Exception {
        TreeMap<Integer, byte[]> original = new TreeMap<>();
        original.put(1, new byte[] {0x01, 0x02, 0x03});
        original.put(2, new byte[] {0x04, 0x05, 0x06});

        byte[] serialized = XMSSUtil.serialize(original);
        TreeMap<Integer, byte[]> restored = deserializeTreeMap(serialized);

        assertEquals(original.keySet(), restored.keySet());
        assertArrayEquals(original.get(1), restored.get(1));
        assertArrayEquals(original.get(2), restored.get(2));
    }

    @SuppressWarnings("unchecked")
    private static TreeMap<Integer, byte[]> deserializeTreeMap(byte[] serialized) throws Exception {
        return (TreeMap<Integer, byte[]>)XMSSUtil.deserialize(serialized, TreeMap.class);
    }

    private static byte[] sequence(int size, int start) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte)(start + i);
        }
        return bytes;
    }
}

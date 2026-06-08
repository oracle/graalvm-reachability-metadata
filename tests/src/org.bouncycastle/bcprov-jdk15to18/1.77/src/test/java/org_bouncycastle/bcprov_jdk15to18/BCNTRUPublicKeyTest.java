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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.PublicKey;

import org.bouncycastle.pqc.crypto.ntru.NTRUParameters;
import org.bouncycastle.pqc.crypto.ntru.NTRUPublicKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.ntru.BCNTRUPublicKey;
import org.junit.jupiter.api.Test;

public class BCNTRUPublicKeyTest {
    @Test
    void javaSerializationPreservesNtruPublicKeyEncoding() throws Exception {
        BCNTRUPublicKey publicKey = createPublicKey();

        byte[] serialized = serialize(publicKey);
        PublicKey restored = deserialize(serialized);
        BCNTRUPublicKey restoredNtruKey = assertInstanceOf(BCNTRUPublicKey.class, restored);

        assertEquals("NTRU", publicKey.getAlgorithm());
        assertEquals(publicKey.getAlgorithm(), restoredNtruKey.getAlgorithm());
        assertEquals("X.509", restoredNtruKey.getFormat());
        assertEquals(
                publicKey.getParameterSpec().getName(),
                restoredNtruKey.getParameterSpec().getName());
        assertArrayEquals(publicKey.getEncoded(), restoredNtruKey.getEncoded());
        assertEquals(publicKey, restoredNtruKey);
        assertEquals(publicKey.hashCode(), restoredNtruKey.hashCode());
    }

    private static BCNTRUPublicKey createPublicKey() {
        NTRUPublicKeyParameters keyParameters = new NTRUPublicKeyParameters(
                NTRUParameters.ntruhps2048509,
                sequence(699, 1));
        return new BCNTRUPublicKey(keyParameters);
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
}

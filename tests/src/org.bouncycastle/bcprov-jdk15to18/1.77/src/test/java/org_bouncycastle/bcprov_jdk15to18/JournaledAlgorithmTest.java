/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.SecureRandom;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.crypto.util.JournaledAlgorithm;
import org.bouncycastle.crypto.util.JournalingSecureRandom;
import org.junit.jupiter.api.Test;

public class JournaledAlgorithmTest {
    private static final byte[] RANDOM_BYTES = new byte[] {
        0x10, 0x21, 0x32, 0x43, 0x54, 0x65, 0x76, (byte)0x87,
    };

    @Test
    void javaSerializationPreservesAlgorithmIdentifierAndRandomTranscript() throws Exception {
        AlgorithmIdentifier algorithmIdentifier = new AlgorithmIdentifier(
            new ASN1ObjectIdentifier("1.2.840.113549.3.7"), DERNull.INSTANCE);
        JournalingSecureRandom journalingSecureRandom = new JournalingSecureRandom(
            new FixedSecureRandom(RANDOM_BYTES));
        JournaledAlgorithm journaledAlgorithm = new JournaledAlgorithm(
            algorithmIdentifier, journalingSecureRandom);
        byte[] generatedBytes = new byte[RANDOM_BYTES.length];
        journalingSecureRandom.nextBytes(generatedBytes);

        byte[] serialized = serialize(journaledAlgorithm);
        JournaledAlgorithm restored = deserialize(serialized);

        byte[] replayedBytes = new byte[RANDOM_BYTES.length];
        restored.getJournalingSecureRandom().nextBytes(replayedBytes);
        assertArrayEquals(generatedBytes, replayedBytes);
        assertArrayEquals(
            algorithmIdentifier.getEncoded(), restored.getAlgorithmIdentifier().getEncoded());
    }

    private static byte[] serialize(JournaledAlgorithm journaledAlgorithm) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(journaledAlgorithm);
        }
        return byteOutputStream.toByteArray();
    }

    private static JournaledAlgorithm deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (JournaledAlgorithm)objectInputStream.readObject();
        }
    }

    private static final class FixedSecureRandom extends SecureRandom {
        private static final long serialVersionUID = 1L;

        private final byte[] bytes;
        private int index;

        private FixedSecureRandom(byte[] bytes) {
            this.bytes = bytes.clone();
        }

        @Override
        public void nextBytes(byte[] target) {
            for (int i = 0; i < target.length; i++) {
                target[i] = bytes[index % bytes.length];
                index++;
            }
        }
    }
}

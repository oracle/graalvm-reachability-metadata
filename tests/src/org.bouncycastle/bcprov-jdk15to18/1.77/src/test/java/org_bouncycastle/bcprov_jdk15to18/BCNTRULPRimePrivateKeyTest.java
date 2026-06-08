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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.provider.ntruprime.BCNTRULPRimePrivateKey;
import org.bouncycastle.pqc.jcajce.spec.NTRULPRimeParameterSpec;
import org.junit.jupiter.api.Test;

public class BCNTRULPRimePrivateKeyTest {
    @Test
    void javaSerializationPreservesNtruLPrimePrivateKeyEncoding() throws Exception {
        BCNTRULPRimePrivateKey privateKey = createPrivateKey();

        byte[] serialized = serialize(privateKey);
        BCNTRULPRimePrivateKey restored = deserialize(serialized);

        assertEquals("NTRULPRime", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(
                privateKey.getParameterSpec().getName(),
                restored.getParameterSpec().getName());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCNTRULPRimePrivateKey createPrivateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                "NTRULPRIME",
                new BouncyCastlePQCProvider());
        generator.initialize(NTRULPRimeParameterSpec.ntrulpr653, new SequenceSecureRandom());
        return (BCNTRULPRimePrivateKey)generator.generateKeyPair().getPrivate();
    }

    private static byte[] serialize(BCNTRULPRimePrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCNTRULPRimePrivateKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (BCNTRULPRimePrivateKey)objectInputStream.readObject();
        }
    }

    private static final class SequenceSecureRandom extends SecureRandom {
        private static final long serialVersionUID = 1L;

        private int nextValue = 1;

        @Override
        public void nextBytes(byte[] bytes) {
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte)nextValue;
                nextValue++;
            }
        }
    }
}

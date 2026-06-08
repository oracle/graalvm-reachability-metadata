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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.SecureRandom;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.pqc.crypto.ntru.NTRUKeyGenerationParameters;
import org.bouncycastle.pqc.crypto.ntru.NTRUKeyPairGenerator;
import org.bouncycastle.pqc.crypto.ntru.NTRUParameters;
import org.bouncycastle.pqc.crypto.ntru.NTRUPrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.ntru.BCNTRUPrivateKey;
import org.junit.jupiter.api.Test;

public class BCNTRUPrivateKeyTest {
    @Test
    void javaSerializationPreservesNtruPrivateKeyEncoding() throws Exception {
        BCNTRUPrivateKey privateKey = createPrivateKey();

        byte[] serialized = serialize(privateKey);
        assertTrue(serialized.length > privateKey.getEncoded().length);
        BCNTRUPrivateKey restored = deserialize(serialized);

        assertEquals("NTRU", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(
                privateKey.getParameterSpec().getName(),
                restored.getParameterSpec().getName());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCNTRUPrivateKey createPrivateKey() {
        NTRUKeyPairGenerator generator = new NTRUKeyPairGenerator();
        generator.init(new NTRUKeyGenerationParameters(
                new SequenceSecureRandom(), NTRUParameters.ntruhps2048509));
        AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();
        return new BCNTRUPrivateKey((NTRUPrivateKeyParameters)keyPair.getPrivate());
    }

    private static byte[] serialize(BCNTRUPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCNTRUPrivateKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (BCNTRUPrivateKey)objectInputStream.readObject();
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

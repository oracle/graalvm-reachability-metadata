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
import java.security.SecureRandom;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.pqc.crypto.hqc.HQCKeyGenerationParameters;
import org.bouncycastle.pqc.crypto.hqc.HQCKeyPairGenerator;
import org.bouncycastle.pqc.crypto.hqc.HQCParameters;
import org.bouncycastle.pqc.crypto.hqc.HQCPrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.provider.hqc.BCHQCPrivateKey;
import org.junit.jupiter.api.Test;

public class BCHQCPrivateKeyTest {
    @Test
    void javaSerializationPreservesHqcPrivateKeyEncoding() throws Exception {
        BCHQCPrivateKey privateKey = createPrivateKey();

        byte[] serialized = serialize(privateKey);
        BCHQCPrivateKey restored = deserialize(serialized, privateKey.getEncoded());

        assertEquals("HQC-128", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCHQCPrivateKey createPrivateKey() {
        HQCKeyPairGenerator generator = new HQCKeyPairGenerator();
        generator.init(new HQCKeyGenerationParameters(new SecureRandom(),
                HQCParameters.hqc128));
        AsymmetricCipherKeyPair keyPair = generator.generateKeyPairWithSeed(
                sequence(48, 1));
        return new BCHQCPrivateKey((HQCPrivateKeyParameters)keyPair.getPrivate());
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCHQCPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new EncodedPrivateKeyReplacingObjectOutputStream(
                byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCHQCPrivateKey deserialize(byte[] serialized, byte[] encodedPrivateKey)
            throws Exception {
        try (ObjectInputStream objectInputStream = new EncodedPrivateKeyResolvingObjectInputStream(
                new ByteArrayInputStream(serialized), encodedPrivateKey)) {
            return (BCHQCPrivateKey)objectInputStream.readObject();
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
                return new BouncyCastlePQCProvider();
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedPrivateKeyResolvingObjectInputStream extends ObjectInputStream {
        private final byte[] encodedPrivateKey;
        private boolean resolvedEncodedKeyCarrier;

        private EncodedPrivateKeyResolvingObjectInputStream(ByteArrayInputStream inputStream,
                byte[] encodedPrivateKey) throws IOException {
            super(inputStream);
            this.encodedPrivateKey = encodedPrivateKey.clone();
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (!resolvedEncodedKeyCarrier && object instanceof BouncyCastlePQCProvider) {
                resolvedEncodedKeyCarrier = true;
                return encodedPrivateKey.clone();
            }
            return super.resolveObject(object);
        }
    }
}

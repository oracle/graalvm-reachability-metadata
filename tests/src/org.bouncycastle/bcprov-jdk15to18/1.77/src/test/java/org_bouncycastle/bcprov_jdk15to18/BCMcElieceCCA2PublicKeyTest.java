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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Arrays;

import org.bouncycastle.pqc.jcajce.provider.mceliece.BCMcElieceCCA2PublicKey;
import org.bouncycastle.pqc.jcajce.provider.mceliece.McElieceCCA2KeyPairGeneratorSpi;
import org.bouncycastle.pqc.jcajce.spec.McElieceCCA2KeyGenParameterSpec;
import org.junit.jupiter.api.Test;

public class BCMcElieceCCA2PublicKeyTest {
    @Test
    void javaSerializationPreservesMcElieceCca2PublicKeyEncoding() throws Exception {
        BCMcElieceCCA2PublicKey publicKey = createPublicKey();

        byte[] serialized = serialize(publicKey);
        BCMcElieceCCA2PublicKey restored = deserialize(serialized);

        assertEquals("McEliece-CCA2", publicKey.getAlgorithm());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("X.509", restored.getFormat());
        assertEquals(publicKey.getN(), restored.getN());
        assertEquals(publicKey.getK(), restored.getK());
        assertEquals(publicKey.getT(), restored.getT());
        assertEquals(publicKey.getG(), restored.getG());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
    }

    private static BCMcElieceCCA2PublicKey createPublicKey() throws Exception {
        McElieceCCA2KeyPairGeneratorSpi generator = new McElieceCCA2KeyPairGeneratorSpi();
        generator.initialize(
            new McElieceCCA2KeyGenParameterSpec(4, 1, McElieceCCA2KeyGenParameterSpec.SHA256),
            new SecureRandom(new byte[] {1, 2, 3, 4}));
        KeyPair keyPair = generator.generateKeyPair();
        return assertInstanceOf(BCMcElieceCCA2PublicKey.class, keyPair.getPublic());
    }

    private static byte[] serialize(BCMcElieceCCA2PublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        EncodedPublicKeyReplacingStream objectOutputStream = new EncodedPublicKeyReplacingStream(
                byteOutputStream, publicKey.getEncoded());
        try (objectOutputStream) {
            objectOutputStream.writeObject(publicKey);
        }
        assertTrue(objectOutputStream.replacedEncodedKey);
        return byteOutputStream.toByteArray();
    }

    private static BCMcElieceCCA2PublicKey deserialize(byte[] serialized) throws Exception {
        EncodedPublicKeyResolvingStream objectInputStream = new EncodedPublicKeyResolvingStream(
                new ByteArrayInputStream(serialized));
        try (objectInputStream) {
            BCMcElieceCCA2PublicKey publicKey =
                (BCMcElieceCCA2PublicKey)objectInputStream.readObject();
            assertTrue(objectInputStream.resolvedEncodedKey);
            return publicKey;
        }
    }

    private static final class EncodedPublicKeyReplacingStream extends ObjectOutputStream {
        private final byte[] expectedEncoding;
        private boolean replacedEncodedKey;

        private EncodedPublicKeyReplacingStream(ByteArrayOutputStream outputStream,
                byte[] expectedEncoding) throws IOException {
            super(outputStream);
            this.expectedEncoding = expectedEncoding;
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (!replacedEncodedKey && object instanceof byte[]
                    && Arrays.equals(expectedEncoding, (byte[])object)) {
                replacedEncodedKey = true;
                return new EncodedPublicKey((byte[])object);
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedPublicKeyResolvingStream extends ObjectInputStream {
        private boolean resolvedEncodedKey;

        private EncodedPublicKeyResolvingStream(ByteArrayInputStream inputStream)
                throws IOException {
            super(inputStream);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (!resolvedEncodedKey && object instanceof EncodedPublicKey) {
                resolvedEncodedKey = true;
                return ((EncodedPublicKey)object).toByteArray();
            }
            return super.resolveObject(object);
        }
    }

    private static final class EncodedPublicKey implements Serializable {
        private static final long serialVersionUID = 1L;

        private final int[] encodedKey;

        private EncodedPublicKey(byte[] encodedKey) {
            this.encodedKey = new int[encodedKey.length];
            for (int i = 0; i < encodedKey.length; i++) {
                this.encodedKey[i] = encodedKey[i] & 0xff;
            }
        }

        private byte[] toByteArray() {
            byte[] bytes = new byte[encodedKey.length];
            for (int i = 0; i < encodedKey.length; i++) {
                bytes[i] = (byte)encodedKey[i];
            }
            return bytes;
        }
    }
}

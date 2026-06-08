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
import java.io.Serializable;
import java.security.SecureRandom;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.KeyGenerationParameters;
import org.bouncycastle.pqc.crypto.newhope.NHKeyPairGenerator;
import org.bouncycastle.pqc.crypto.newhope.NHPublicKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.newhope.BCNHPublicKey;
import org.junit.jupiter.api.Test;

public class BCNHPublicKeyTest {
    @Test
    void javaSerializationWritesGeneratedNewHopePublicKeyEncoding() throws Exception {
        BCNHPublicKey publicKey = createPublicKey();

        byte[] serialized = serialize(publicKey);

        assertTrue(serialized.length > publicKey.getEncoded().length);
    }

    @Test
    void javaSerializationPreservesGeneratedNewHopePublicKeyEncoding() throws Exception {
        BCNHPublicKey publicKey = createPublicKey();

        BCNHPublicKey restored = deserialize(serialize(publicKey), publicKey.getEncoded());

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    @Test
    void javaSerializationPreservesDecodedNewHopePublicKeyEncoding() throws Exception {
        BCNHPublicKey generatedKey = createPublicKey();
        SubjectPublicKeyInfo keyInfo = SubjectPublicKeyInfo.getInstance(generatedKey.getEncoded());
        BCNHPublicKey decodedKey = new BCNHPublicKey(keyInfo);

        BCNHPublicKey restored = deserialize(serialize(decodedKey), decodedKey.getEncoded());

        assertPublicKeyRoundTrip(decodedKey, restored);
    }

    private static void assertPublicKeyRoundTrip(
            BCNHPublicKey publicKey,
            BCNHPublicKey restored) {
        assertEquals("NH", publicKey.getAlgorithm());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("X.509", restored.getFormat());
        assertArrayEquals(publicKey.getPublicData(), restored.getPublicData());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
    }

    private static BCNHPublicKey createPublicKey() {
        NHKeyPairGenerator keyPairGenerator = new NHKeyPairGenerator();
        SecureRandom secureRandom = new SecureRandom(new byte[] {1, 2, 3, 4});
        keyPairGenerator.init(new KeyGenerationParameters(secureRandom, 1024));
        AsymmetricCipherKeyPair keyPair = keyPairGenerator.generateKeyPair();
        NHPublicKeyParameters publicKeyParameters = (NHPublicKeyParameters)keyPair.getPublic();
        return new BCNHPublicKey(publicKeyParameters);
    }

    private static byte[] serialize(BCNHPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedPublicKeyReplacingStream objectOutputStream =
                new EncodedPublicKeyReplacingStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
            assertTrue(objectOutputStream.hasReplacedEncodedKey());
        }
        return byteOutputStream.toByteArray();
    }

    private static BCNHPublicKey deserialize(
            byte[] serialized,
            byte[] expectedEncoded) throws Exception {
        try (EncodedPublicKeyResolvingStream objectInputStream =
                new EncodedPublicKeyResolvingStream(new ByteArrayInputStream(serialized))) {
            BCNHPublicKey publicKey = (BCNHPublicKey)objectInputStream.readObject();
            assertTrue(objectInputStream.hasResolvedEncodedKey());
            assertArrayEquals(expectedEncoded, publicKey.getEncoded());
            return publicKey;
        }
    }

    private static final class EncodedPublicKeyReplacingStream extends ObjectOutputStream {
        private boolean replacedEncodedKey;

        private EncodedPublicKeyReplacingStream(ByteArrayOutputStream outputStream)
                throws IOException {
            super(outputStream);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (!replacedEncodedKey && object instanceof byte[]) {
                replacedEncodedKey = true;
                return new EncodedPublicKey((byte[])object);
            }
            return super.replaceObject(object);
        }

        private boolean hasReplacedEncodedKey() {
            return replacedEncodedKey;
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

        private boolean hasResolvedEncodedKey() {
            return resolvedEncodedKey;
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

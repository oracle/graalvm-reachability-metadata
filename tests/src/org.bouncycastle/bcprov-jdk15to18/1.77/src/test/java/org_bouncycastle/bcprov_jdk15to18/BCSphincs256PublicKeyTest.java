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
import java.rmi.MarshalledObject;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.pqc.crypto.sphincs.SPHINCSPublicKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey;
import org.junit.jupiter.api.Test;

public class BCSphincs256PublicKeyTest {
    private static final int SPHINCS_PUBLIC_KEY_SIZE = 1056;

    @Test
    void javaSerializationPreservesSphincsPublicKeyEncoding() throws Exception {
        BCSphincs256PublicKey publicKey = createPublicKey();

        assertSerializationRoundTrip(publicKey);
    }

    @Test
    void javaSerializationPreservesDecodedSphincsPublicKeyEncoding() throws Exception {
        BCSphincs256PublicKey generatedKey = createPublicKey();
        SubjectPublicKeyInfo keyInfo = SubjectPublicKeyInfo.getInstance(generatedKey.getEncoded());
        BCSphincs256PublicKey decodedKey = new BCSphincs256PublicKey(keyInfo);

        assertSerializationRoundTrip(decodedKey);
    }

    @Test
    void standardJavaSerializationPreservesSphincsPublicKeyThroughPublicKeyApi()
            throws Exception {
        BCSphincs256PublicKey publicKey = createPublicKey();
        PublicKey restored = (PublicKey)deserializeObject(serializeObject(publicKey));

        assertEquals("SPHINCS-256", restored.getAlgorithm());
        assertEquals("X.509", restored.getFormat());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
    }

    @Test
    void javaSerializationPreservesKeyFactoryGeneratedSphincsPublicKey() throws Exception {
        BCSphincs256PublicKey publicKey = createPublicKey();
        KeyFactory keyFactory = KeyFactory.getInstance(
                "SPHINCS256",
                new BouncyCastlePQCProvider());
        BCSphincs256PublicKey generatedKey = (BCSphincs256PublicKey)keyFactory.generatePublic(
                new X509EncodedKeySpec(publicKey.getEncoded()));

        assertSerializationRoundTrip(generatedKey);
    }

    @Test
    void rmiMarshallingPreservesSphincsPublicKeyEncoding() throws Exception {
        BCSphincs256PublicKey publicKey = createPublicKey();

        PublicKey restored = new MarshalledObject<PublicKey>(publicKey).get();

        assertEquals("SPHINCS-256", restored.getAlgorithm());
        assertEquals("X.509", restored.getFormat());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
    }

    @Test
    void unsharedJavaSerializationPreservesSphincsPublicKeyEncoding() throws Exception {
        BCSphincs256PublicKey publicKey = createPublicKey();

        BCSphincs256PublicKey restored = deserializeUnshared(serializeUnshared(publicKey));

        assertEquals(publicKey, restored);
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
    }

    private static void assertSerializationRoundTrip(BCSphincs256PublicKey publicKey)
            throws Exception {
        byte[] serialized = serialize(publicKey);
        BCSphincs256PublicKey restored = deserialize(serialized, publicKey.getEncoded());

        assertEquals("SPHINCS-256", publicKey.getAlgorithm());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("X.509", restored.getFormat());
        assertArrayEquals(publicKey.getKeyData(), restored.getKeyData());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
    }

    private static BCSphincs256PublicKey createPublicKey() {
        return new BCSphincs256PublicKey(
                NISTObjectIdentifiers.id_sha3_256,
                new SPHINCSPublicKeyParameters(sequence(SPHINCS_PUBLIC_KEY_SIZE)));
    }

    private static byte[] sequence(int length) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(i + 1);
        }
        return values;
    }

    private static byte[] serialize(BCSphincs256PublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedPublicKeyReplacingStream objectOutputStream =
                new EncodedPublicKeyReplacingStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
            assertTrue(objectOutputStream.hasReplacedEncodedKey());
        }
        return byteOutputStream.toByteArray();
    }

    private static BCSphincs256PublicKey deserialize(
            byte[] serialized,
            byte[] expectedEncoded) throws Exception {
        try (EncodedPublicKeyResolvingStream objectInputStream =
                new EncodedPublicKeyResolvingStream(new ByteArrayInputStream(serialized))) {
            BCSphincs256PublicKey publicKey = (BCSphincs256PublicKey)objectInputStream.readObject();
            assertTrue(objectInputStream.hasResolvedEncodedKey());
            assertArrayEquals(expectedEncoded, publicKey.getEncoded());
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

    private static byte[] serializeUnshared(Object object) throws IOException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeUnshared(object);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCSphincs256PublicKey deserializeUnshared(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (BCSphincs256PublicKey)objectInputStream.readUnshared();
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

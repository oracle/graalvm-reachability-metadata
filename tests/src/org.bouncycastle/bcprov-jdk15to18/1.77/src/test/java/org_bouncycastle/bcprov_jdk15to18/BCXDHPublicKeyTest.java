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
import java.io.ObjectStreamClass;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.jcajce.interfaces.XDHPublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCXDHPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class BCXDHPublicKeyTest {
    private static final String BC_XDH_PUBLIC_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.edec.BCXDHPublicKey";

    @Test
    void javaSerializationPreservesX25519PublicKeyEncoding() throws Exception {
        assertJavaSerializationPreservesXdhPublicKeyEncoding(generateX25519PublicKey());
    }

    @Test
    void javaSerializationPreservesX448PublicKeyEncoding() throws Exception {
        assertJavaSerializationPreservesXdhPublicKeyEncoding(generateX448PublicKey());
    }

    @Test
    void javaSerializationPreservesKeyFactoryX25519PublicKeyEncoding() throws Exception {
        assertJavaSerializationPreservesXdhPublicKeyEncoding(
            recreateWithKeyFactory("X25519", generateX25519PublicKey()));
    }

    @Test
    void javaSerializationPreservesKeyFactoryX448PublicKeyEncoding() throws Exception {
        assertJavaSerializationPreservesXdhPublicKeyEncoding(
            recreateWithKeyFactory("X448", generateX448PublicKey()));
    }

    private static void assertJavaSerializationPreservesXdhPublicKeyEncoding(
            BCXDHPublicKey publicKey) throws Exception {
        BCXDHPublicKeyObjectOutputStream objectOutputStream =
            new BCXDHPublicKeyObjectOutputStream();
        byte[] serialized = objectOutputStream.serialize(publicKey);
        BCXDHPublicKeyObjectInputStream objectInputStream =
            new BCXDHPublicKeyObjectInputStream(serialized);
        XDHPublicKey restored = objectInputStream.deserialize();
        XDHPublicKey standardRestored = deserializeWithStandardObjectStream(
            serializeWithStandardObjectStream(publicKey));

        assertTrue(objectOutputStream.sawEncodedKey());
        assertTrue(objectInputStream.sawEncodedKey());

        assertEquivalentPublicKey(publicKey, restored);
        assertEquivalentPublicKey(publicKey, standardRestored);
    }

    private static void assertEquivalentPublicKey(
            BCXDHPublicKey publicKey, XDHPublicKey restored) {
        assertEquals(BC_XDH_PUBLIC_KEY_CLASS, publicKey.getClass().getName());
        assertEquals(BC_XDH_PUBLIC_KEY_CLASS, restored.getClass().getName());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals(publicKey.getFormat(), restored.getFormat());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
        assertEquals(publicKey.getU(), restored.getU());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertArrayEquals(publicKey.getUEncoding(), restored.getUEncoding());
    }

    private static byte[] serializeWithStandardObjectStream(PublicKey publicKey)
            throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static XDHPublicKey deserializeWithStandardObjectStream(byte[] serialized)
            throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (XDHPublicKey)objectInputStream.readObject();
        }
    }

    private static BCXDHPublicKey generateX25519PublicKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("X25519", bcProvider());
        keyPairGenerator.initialize(255, new SecureRandom(new byte[] {1, 2, 3, 4}));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return (BCXDHPublicKey)keyPair.getPublic();
    }

    private static BCXDHPublicKey generateX448PublicKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("X448", bcProvider());
        keyPairGenerator.initialize(448, new SecureRandom(new byte[] {4, 3, 2, 1}));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return (BCXDHPublicKey)keyPair.getPublic();
    }

    private static BCXDHPublicKey recreateWithKeyFactory(
            String algorithm, BCXDHPublicKey publicKey) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(algorithm, bcProvider());
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKey.getEncoded());
        PublicKey recreated = keyFactory.generatePublic(keySpec);
        return (BCXDHPublicKey)recreated;
    }

    private static Provider bcProvider() {
        return new BouncyCastleProvider();
    }

    private static final class BCXDHPublicKeyObjectOutputStream extends ObjectOutputStream {
        private final ByteArrayOutputStream byteOutputStream;
        private boolean encodedKeySeen;

        private BCXDHPublicKeyObjectOutputStream() throws Exception {
            this(new ByteArrayOutputStream());
        }

        private BCXDHPublicKeyObjectOutputStream(ByteArrayOutputStream byteOutputStream)
                throws Exception {
            super(byteOutputStream);
            this.byteOutputStream = byteOutputStream;
            enableReplaceObject(true);
        }

        private byte[] serialize(PublicKey publicKey) throws Exception {
            try {
                writeObject(publicKey);
            }
            finally {
                close();
            }
            return byteOutputStream.toByteArray();
        }

        private boolean sawEncodedKey() {
            return encodedKeySeen;
        }

        @Override
        protected Object replaceObject(Object object) {
            if (object instanceof byte[]) {
                encodedKeySeen = true;
            }
            return object;
        }
    }

    private static final class BCXDHPublicKeyObjectInputStream extends ObjectInputStream {
        private boolean encodedKeySeen;

        private BCXDHPublicKeyObjectInputStream(byte[] serialized) throws Exception {
            super(new ByteArrayInputStream(serialized));
            enableResolveObject(true);
        }

        private XDHPublicKey deserialize() throws Exception {
            try {
                return (XDHPublicKey)readObject();
            }
            finally {
                close();
            }
        }

        private boolean sawEncodedKey() {
            return encodedKeySeen;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor)
                throws IOException, ClassNotFoundException {
            if (BC_XDH_PUBLIC_KEY_CLASS.equals(descriptor.getName())) {
                return BCXDHPublicKey.class;
            }
            return super.resolveClass(descriptor);
        }

        @Override
        protected Object resolveObject(Object object) {
            if (object instanceof byte[]) {
                encodedKeySeen = true;
            }
            return object;
        }
    }
}

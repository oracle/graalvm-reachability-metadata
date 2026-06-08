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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;

import org.bouncycastle.jcajce.interfaces.XDHPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCXDHPrivateKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class BCXDHPrivateKeyTest {
    private static final String BC_XDH_PRIVATE_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.edec.BCXDHPrivateKey";

    @Test
    void javaSerializationPreservesX25519PrivateKeyEncoding() throws Exception {
        assertJavaSerializationPreservesXdhPrivateKeyEncoding(generateX25519PrivateKey());
    }

    @Test
    void javaSerializationPreservesX448PrivateKeyEncoding() throws Exception {
        assertJavaSerializationPreservesXdhPrivateKeyEncoding(generateX448PrivateKey());
    }

    private static void assertJavaSerializationPreservesXdhPrivateKeyEncoding(
            BCXDHPrivateKey privateKey) throws Exception {
        BCXDHPrivateKeyObjectOutputStream objectOutputStream =
            new BCXDHPrivateKeyObjectOutputStream();
        byte[] serialized = objectOutputStream.serialize(privateKey);
        BCXDHPrivateKeyObjectInputStream objectInputStream =
            new BCXDHPrivateKeyObjectInputStream(serialized);
        XDHPrivateKey restored = objectInputStream.deserialize();
        XDHPrivateKey standardRestored = deserializeWithStandardObjectStream(
            serializeWithStandardObjectStream(privateKey));

        assertTrue(objectOutputStream.sawEncodedKey());
        assertTrue(objectInputStream.sawEncodedKey());

        assertEquivalentPrivateKey(privateKey, restored);
        assertEquivalentPrivateKey(privateKey, standardRestored);
    }

    private static void assertEquivalentPrivateKey(
            BCXDHPrivateKey privateKey, XDHPrivateKey restored) {
        assertEquals(BC_XDH_PRIVATE_KEY_CLASS, privateKey.getClass().getName());
        assertEquals(BC_XDH_PRIVATE_KEY_CLASS, restored.getClass().getName());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals(privateKey.getFormat(), restored.getFormat());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertArrayEquals(
            privateKey.getPublicKey().getUEncoding(),
            restored.getPublicKey().getUEncoding());
    }

    private static byte[] serializeWithStandardObjectStream(PrivateKey privateKey)
            throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static XDHPrivateKey deserializeWithStandardObjectStream(byte[] serialized)
            throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (XDHPrivateKey)objectInputStream.readObject();
        }
    }

    private static BCXDHPrivateKey generateX25519PrivateKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("X25519", bcProvider());
        keyPairGenerator.initialize(255, new SecureRandom(new byte[] {1, 2, 3, 4}));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return (BCXDHPrivateKey)keyPair.getPrivate();
    }

    private static BCXDHPrivateKey generateX448PrivateKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("X448", bcProvider());
        keyPairGenerator.initialize(448, new SecureRandom(new byte[] {4, 3, 2, 1}));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return (BCXDHPrivateKey)keyPair.getPrivate();
    }

    private static Provider bcProvider() {
        return new BouncyCastleProvider();
    }

    private static final class BCXDHPrivateKeyObjectOutputStream extends ObjectOutputStream {
        private final ByteArrayOutputStream byteOutputStream;
        private boolean encodedKeySeen;

        private BCXDHPrivateKeyObjectOutputStream() throws Exception {
            this(new ByteArrayOutputStream());
        }

        private BCXDHPrivateKeyObjectOutputStream(ByteArrayOutputStream byteOutputStream)
                throws Exception {
            super(byteOutputStream);
            this.byteOutputStream = byteOutputStream;
            enableReplaceObject(true);
        }

        private byte[] serialize(PrivateKey privateKey) throws Exception {
            try {
                writeObject(privateKey);
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

    private static final class BCXDHPrivateKeyObjectInputStream extends ObjectInputStream {
        private boolean encodedKeySeen;

        private BCXDHPrivateKeyObjectInputStream(byte[] serialized) throws Exception {
            super(new ByteArrayInputStream(serialized));
            enableResolveObject(true);
        }

        private XDHPrivateKey deserialize() throws Exception {
            try {
                return (XDHPrivateKey)readObject();
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
            if (BC_XDH_PRIVATE_KEY_CLASS.equals(descriptor.getName())) {
                return BCXDHPrivateKey.class;
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

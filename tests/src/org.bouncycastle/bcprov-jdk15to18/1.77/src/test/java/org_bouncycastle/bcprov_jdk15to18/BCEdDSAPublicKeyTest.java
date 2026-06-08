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
import java.security.PublicKey;
import java.security.SecureRandom;

import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.edec.KeyPairGeneratorSpi.Ed25519;
import org.bouncycastle.jcajce.provider.asymmetric.edec.KeyPairGeneratorSpi.Ed448;
import org.junit.jupiter.api.Test;

public class BCEdDSAPublicKeyTest {
    private static final String BC_EDDSA_PUBLIC_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPublicKey";

    @Test
    void javaSerializationPreservesEd25519PublicKeyEncoding() throws Exception {
        assertJavaSerializationPreservesEdDsaPublicKeyEncoding(generateEd25519PublicKey());
    }

    @Test
    void javaSerializationPreservesEd448PublicKeyEncoding() throws Exception {
        assertJavaSerializationPreservesEdDsaPublicKeyEncoding(generateEd448PublicKey());
    }

    private static void assertJavaSerializationPreservesEdDsaPublicKeyEncoding(
            BCEdDSAPublicKey publicKey) throws Exception {
        BCEdDSAPublicKeyObjectOutputStream objectOutputStream =
            new BCEdDSAPublicKeyObjectOutputStream();
        byte[] serialized = objectOutputStream.serialize(publicKey);
        BCEdDSAPublicKeyObjectInputStream objectInputStream =
            new BCEdDSAPublicKeyObjectInputStream(serialized);
        PublicKey restored = objectInputStream.deserialize();

        assertTrue(objectOutputStream.sawEncodedKey());
        assertTrue(objectInputStream.sawEncodedKey());

        assertEquals(BC_EDDSA_PUBLIC_KEY_CLASS, publicKey.getClass().getName());
        assertEquals(BC_EDDSA_PUBLIC_KEY_CLASS, restored.getClass().getName());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals(publicKey.getFormat(), restored.getFormat());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertArrayEquals(
            publicKey.getPointEncoding(),
            ((BCEdDSAPublicKey)restored).getPointEncoding());
    }

    private static BCEdDSAPublicKey generateEd25519PublicKey() {
        Ed25519 keyPairGenerator = new Ed25519();
        keyPairGenerator.initialize(255, new SecureRandom(new byte[] {1, 2, 3, 4}));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return (BCEdDSAPublicKey)keyPair.getPublic();
    }

    private static BCEdDSAPublicKey generateEd448PublicKey() {
        Ed448 keyPairGenerator = new Ed448();
        keyPairGenerator.initialize(448, new SecureRandom(new byte[] {4, 3, 2, 1}));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return (BCEdDSAPublicKey)keyPair.getPublic();
    }

    private static final class BCEdDSAPublicKeyObjectOutputStream extends ObjectOutputStream {
        private final ByteArrayOutputStream byteOutputStream;
        private boolean encodedKeySeen;

        private BCEdDSAPublicKeyObjectOutputStream() throws Exception {
            this(new ByteArrayOutputStream());
        }

        private BCEdDSAPublicKeyObjectOutputStream(ByteArrayOutputStream byteOutputStream)
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

    private static final class BCEdDSAPublicKeyObjectInputStream extends ObjectInputStream {
        private boolean encodedKeySeen;

        private BCEdDSAPublicKeyObjectInputStream(byte[] serialized) throws Exception {
            super(new ByteArrayInputStream(serialized));
            enableResolveObject(true);
        }

        private PublicKey deserialize() throws Exception {
            try {
                return (PublicKey)readObject();
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
            if (BC_EDDSA_PUBLIC_KEY_CLASS.equals(descriptor.getName())) {
                return BCEdDSAPublicKey.class;
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

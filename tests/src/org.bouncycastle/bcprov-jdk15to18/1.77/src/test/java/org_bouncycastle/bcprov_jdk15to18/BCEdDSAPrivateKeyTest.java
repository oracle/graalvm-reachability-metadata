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
import java.security.PrivateKey;
import java.security.SecureRandom;

import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.edec.KeyPairGeneratorSpi.Ed25519;
import org.junit.jupiter.api.Test;

public class BCEdDSAPrivateKeyTest {
    private static final String BC_EDDSA_PRIVATE_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPrivateKey";

    @Test
    void javaSerializationPreservesEdDsaPrivateKeyEncoding() throws Exception {
        BCEdDSAPrivateKey privateKey = generatePrivateKey();

        BCEdDSAPrivateKeyObjectOutputStream objectOutputStream =
            new BCEdDSAPrivateKeyObjectOutputStream();
        byte[] serialized = objectOutputStream.serialize(privateKey);
        BCEdDSAPrivateKeyObjectInputStream objectInputStream =
            new BCEdDSAPrivateKeyObjectInputStream(serialized);
        PrivateKey restored = objectInputStream.deserialize();

        assertTrue(objectOutputStream.sawEncodedKey());
        assertTrue(objectInputStream.sawEncodedKey());

        assertEquals(BC_EDDSA_PRIVATE_KEY_CLASS, privateKey.getClass().getName());
        assertEquals(BC_EDDSA_PRIVATE_KEY_CLASS, restored.getClass().getName());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals(privateKey.getFormat(), restored.getFormat());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
    }

    private static BCEdDSAPrivateKey generatePrivateKey() throws Exception {
        Ed25519 keyPairGenerator = new Ed25519();
        keyPairGenerator.initialize(255, new SecureRandom(new byte[] {1, 2, 3, 4}));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return (BCEdDSAPrivateKey)keyPair.getPrivate();
    }

    private static final class BCEdDSAPrivateKeyObjectOutputStream extends ObjectOutputStream {
        private final ByteArrayOutputStream byteOutputStream;
        private boolean encodedKeySeen;

        private BCEdDSAPrivateKeyObjectOutputStream() throws Exception {
            this(new ByteArrayOutputStream());
        }

        private BCEdDSAPrivateKeyObjectOutputStream(ByteArrayOutputStream byteOutputStream)
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

    private static final class BCEdDSAPrivateKeyObjectInputStream extends ObjectInputStream {
        private boolean encodedKeySeen;

        private BCEdDSAPrivateKeyObjectInputStream(byte[] serialized) throws Exception {
            super(new ByteArrayInputStream(serialized));
            enableResolveObject(true);
        }

        private PrivateKey deserialize() throws Exception {
            try {
                return (PrivateKey)readObject();
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
            if (BC_EDDSA_PRIVATE_KEY_CLASS.equals(descriptor.getName())) {
                return BCEdDSAPrivateKey.class;
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

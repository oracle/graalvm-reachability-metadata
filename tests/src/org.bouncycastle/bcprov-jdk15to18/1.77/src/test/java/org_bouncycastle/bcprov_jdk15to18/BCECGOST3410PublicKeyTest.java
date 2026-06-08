/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;

import org.bouncycastle.jcajce.provider.asymmetric.ecgost.BCECGOST3410PublicKey;
import org.bouncycastle.jcajce.spec.GOST3410ParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class BCECGOST3410PublicKeyTest {
    private static final String BCECGOST3410_PUBLIC_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.ecgost.BCECGOST3410PublicKey";
    private static final String CURVE_NAME = "GostR3410-2001-CryptoPro-A";

    @Test
    void javaSerializationPreservesEcGost3410PublicKeyParameters() throws Exception {
        BCECGOST3410PublicKey publicKey = generatePublicKey();

        byte[] serialized = serialize(publicKey);
        ECPublicKey restored = deserialize(serialized);

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    @Test
    void javaSerializationWritesAndReadsEncodedEcGost3410PublicKey() throws Exception {
        BCECGOST3410PublicKey publicKey = generatePublicKey();

        BCECGOST3410PublicKeyObjectOutputStream objectOutputStream =
            new BCECGOST3410PublicKeyObjectOutputStream();
        byte[] serialized = objectOutputStream.serialize(publicKey);
        BCECGOST3410PublicKeyObjectInputStream objectInputStream =
            new BCECGOST3410PublicKeyObjectInputStream(serialized);
        ECPublicKey restored = objectInputStream.deserialize();

        assertTrue(objectOutputStream.sawEncodedKey());
        assertTrue(objectInputStream.sawEncodedKey());
        assertPublicKeyRoundTrip(publicKey, restored);
    }

    private static BCECGOST3410PublicKey generatePublicKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
            "ECGOST3410", new BouncyCastleProvider());
        keyPairGenerator.initialize(
            new GOST3410ParameterSpec(CURVE_NAME),
            new SecureRandom(new byte[] { 1, 2, 3, 4 }));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return (BCECGOST3410PublicKey)keyPair.getPublic();
    }

    private static void assertPublicKeyRoundTrip(
            ECPublicKey publicKey,
            ECPublicKey restored) {
        assertEquals(BCECGOST3410_PUBLIC_KEY_CLASS, publicKey.getClass().getName());
        assertEquals(BCECGOST3410_PUBLIC_KEY_CLASS, restored.getClass().getName());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals(publicKey.getFormat(), restored.getFormat());
        assertEquals(publicKey.getW(), restored.getW());
        assertParameters(publicKey.getParams(), restored.getParams());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
        assertNotNull(restored.toString());
    }

    private static byte[] serialize(PublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static ECPublicKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (ECPublicKey)objectInputStream.readObject();
        }
    }

    private static void assertParameters(
            ECParameterSpec expected,
            ECParameterSpec actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.getOrder(), actual.getOrder());
        assertEquals(expected.getCofactor(), actual.getCofactor());
        assertEquals(expected.getCurve(), actual.getCurve());
        assertEquals(expected.getGenerator(), actual.getGenerator());
    }

    private static final class BCECGOST3410PublicKeyObjectOutputStream
            extends ObjectOutputStream {
        private final ByteArrayOutputStream byteOutputStream;
        private boolean encodedKeySeen;

        private BCECGOST3410PublicKeyObjectOutputStream() throws IOException {
            this(new ByteArrayOutputStream());
        }

        private BCECGOST3410PublicKeyObjectOutputStream(
                ByteArrayOutputStream byteOutputStream) throws IOException {
            super(byteOutputStream);
            this.byteOutputStream = byteOutputStream;
            enableReplaceObject(true);
        }

        private byte[] serialize(ECPublicKey publicKey) throws IOException {
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
        protected Object replaceObject(Object object) throws IOException {
            if (object instanceof byte[]) {
                encodedKeySeen = true;
            }
            return object;
        }
    }

    private static final class BCECGOST3410PublicKeyObjectInputStream
            extends ObjectInputStream {
        private boolean encodedKeySeen;

        private BCECGOST3410PublicKeyObjectInputStream(byte[] serialized) throws IOException {
            super(new ByteArrayInputStream(serialized));
            enableResolveObject(true);
        }

        private ECPublicKey deserialize() throws IOException, ClassNotFoundException {
            try {
                return (ECPublicKey)readObject();
            }
            finally {
                close();
            }
        }

        private boolean sawEncodedKey() {
            return encodedKeySeen;
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (object instanceof byte[]) {
                encodedKeySeen = true;
            }
            return object;
        }
    }
}

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
import java.math.BigInteger;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECParameterSpec;

import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.junit.jupiter.api.Test;

public class BCECPrivateKeyTest {
    private static final String BCEC_PRIVATE_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey";
    private static final BigInteger PRIVATE_VALUE = BigInteger.valueOf(19L);

    @Test
    void javaSerializationPreservesEcPrivateKeyParameters() throws Exception {
        BCECPrivateKey privateKey = generatePrivateKey();

        BCECPrivateKeyObjectOutputStream objectOutputStream =
            new BCECPrivateKeyObjectOutputStream();
        byte[] serialized = objectOutputStream.serialize(privateKey);
        BCECPrivateKeyObjectInputStream objectInputStream = new BCECPrivateKeyObjectInputStream(
            serialized);
        ECPrivateKey restored = objectInputStream.deserialize();

        assertTrue(objectOutputStream.sawEncodedKey());
        assertTrue(objectInputStream.sawEncodedKey());

        assertEquals(BCEC_PRIVATE_KEY_CLASS, privateKey.getClass().getName());
        assertEquals(BCEC_PRIVATE_KEY_CLASS, restored.getClass().getName());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals(privateKey.getFormat(), restored.getFormat());
        assertEquals(privateKey.getS(), restored.getS());
        assertParameters(privateKey.getParams(), restored.getParams());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
    }

    private static BCECPrivateKey generatePrivateKey() {
        ECNamedCurveParameterSpec curveSpec = ECNamedCurveTable.getParameterSpec("secp256r1");
        return new BCECPrivateKey(
            "EC",
            new ECPrivateKeySpec(PRIVATE_VALUE, curveSpec),
            BouncyCastleProvider.CONFIGURATION);
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

    private static final class BCECPrivateKeyObjectOutputStream extends ObjectOutputStream {
        private final ByteArrayOutputStream byteOutputStream;
        private boolean encodedKeySeen;

        private BCECPrivateKeyObjectOutputStream() throws IOException {
            this(new ByteArrayOutputStream());
        }

        private BCECPrivateKeyObjectOutputStream(ByteArrayOutputStream byteOutputStream)
                throws IOException {
            super(byteOutputStream);
            this.byteOutputStream = byteOutputStream;
            enableReplaceObject(true);
        }

        private byte[] serialize(ECPrivateKey privateKey) throws IOException {
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
        protected Object replaceObject(Object object) throws IOException {
            if (object instanceof byte[]) {
                encodedKeySeen = true;
            }
            return object;
        }
    }

    private static final class BCECPrivateKeyObjectInputStream extends ObjectInputStream {
        private boolean encodedKeySeen;

        private BCECPrivateKeyObjectInputStream(byte[] serialized) throws IOException {
            super(new ByteArrayInputStream(serialized));
            enableResolveObject(true);
        }

        private ECPrivateKey deserialize() throws IOException, ClassNotFoundException {
            try {
                return (ECPrivateKey)readObject();
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

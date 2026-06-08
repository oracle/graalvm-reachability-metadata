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
import java.security.AlgorithmParameters;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;

import org.bouncycastle.jcajce.provider.asymmetric.ecgost.BCECGOST3410PrivateKey;
import org.junit.jupiter.api.Test;

public class BCECGOST3410PrivateKeyTest {
    private static final String BCECGOST3410_PRIVATE_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.ecgost.BCECGOST3410PrivateKey";
    private static final BigInteger PRIVATE_VALUE = BigInteger.valueOf(23L);

    @Test
    void javaSerializationPreservesEcGost3410PrivateKeyParameters() throws Exception {
        ECPrivateKey privateKey = generatePrivateKey();

        BCECGOST3410PrivateKeyObjectOutputStream objectOutputStream =
            new BCECGOST3410PrivateKeyObjectOutputStream();
        byte[] serialized = objectOutputStream.serialize(privateKey);
        BCECGOST3410PrivateKeyObjectInputStream objectInputStream =
            new BCECGOST3410PrivateKeyObjectInputStream(serialized);
        ECPrivateKey restored = objectInputStream.deserialize();

        assertTrue(objectOutputStream.sawEncodedKey());
        assertTrue(objectInputStream.sawEncodedKey());

        assertEquals(BCECGOST3410_PRIVATE_KEY_CLASS, privateKey.getClass().getName());
        assertEquals(BCECGOST3410_PRIVATE_KEY_CLASS, restored.getClass().getName());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals(privateKey.getFormat(), restored.getFormat());
        assertEquals(privateKey.getS(), restored.getS());
        assertParameters(privateKey.getParams(), restored.getParams());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
    }

    private static BCECGOST3410PrivateKey generatePrivateKey() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec parameterSpec = parameters.getParameterSpec(ECParameterSpec.class);
        return new BCECGOST3410PrivateKey(
            new ECPrivateKeySpec(PRIVATE_VALUE, parameterSpec));
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

    private static final class BCECGOST3410PrivateKeyObjectOutputStream
            extends ObjectOutputStream {
        private final ByteArrayOutputStream byteOutputStream;
        private boolean encodedKeySeen;

        private BCECGOST3410PrivateKeyObjectOutputStream() throws IOException {
            this(new ByteArrayOutputStream());
        }

        private BCECGOST3410PrivateKeyObjectOutputStream(
                ByteArrayOutputStream byteOutputStream) throws IOException {
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

    private static final class BCECGOST3410PrivateKeyObjectInputStream
            extends ObjectInputStream {
        private boolean encodedKeySeen;

        private BCECGOST3410PrivateKeyObjectInputStream(byte[] serialized) throws IOException {
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

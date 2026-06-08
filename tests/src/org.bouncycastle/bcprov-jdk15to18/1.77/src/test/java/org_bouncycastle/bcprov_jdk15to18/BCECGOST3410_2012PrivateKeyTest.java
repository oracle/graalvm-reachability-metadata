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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;

import org.bouncycastle.jcajce.provider.asymmetric.ecgost12.BCECGOST3410_2012PrivateKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class BCECGOST3410_2012PrivateKeyTest {
    private static final String BCECGOST3410_2012_PRIVATE_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.ecgost12.BCECGOST3410_2012PrivateKey";
    private static final String CURVE_NAME = "Tc26-Gost-3410-12-256-paramSetA";

    @Test
    void javaSerializationPreservesEcGost3410_2012PrivateKeyParameters() throws Exception {
        ECPrivateKey privateKey = generatePrivateKey();

        byte[] serialized = serialize(privateKey);
        ECPrivateKey restored = deserialize(serialized);

        assertEquals(BCECGOST3410_2012_PRIVATE_KEY_CLASS, privateKey.getClass().getName());
        assertEquals(BCECGOST3410_2012_PRIVATE_KEY_CLASS, restored.getClass().getName());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals(privateKey.getFormat(), restored.getFormat());
        assertEquals(privateKey.getS(), restored.getS());
        assertParameters(privateKey.getParams(), restored.getParams());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
    }

    private static BCECGOST3410_2012PrivateKey generatePrivateKey() throws Exception {
        Provider provider = new BouncyCastleProvider();
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
            "ECGOST3410-2012",
            provider);
        keyPairGenerator.initialize(
            new ECGenParameterSpec(CURVE_NAME),
            new SecureRandom(new byte[] {1, 2, 3, 4}));
        return (BCECGOST3410_2012PrivateKey)keyPairGenerator.generateKeyPair().getPrivate();
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

    private static byte[] serialize(ECPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static ECPrivateKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (ECPrivateKey)objectInputStream.readObject();
        }
    }
}

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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class BCECPublicKeyTest {
    private static final String BCEC_PUBLIC_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey";

    @Test
    void javaSerializationPreservesEcPublicKeyParameters() throws Exception {
        ECPublicKey publicKey = generatePublicKey();

        byte[] serialized = serialize(publicKey);
        ECPublicKey restored = deserialize(serialized);

        assertEquals(BCEC_PUBLIC_KEY_CLASS, publicKey.getClass().getName());
        assertEquals(BCEC_PUBLIC_KEY_CLASS, restored.getClass().getName());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals(publicKey.getFormat(), restored.getFormat());
        assertEquals(publicKey.getW(), restored.getW());
        assertParameters(publicKey.getParams(), restored.getParams());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
    }

    private static ECPublicKey generatePublicKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
            "EC", new BouncyCastleProvider());
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return (ECPublicKey)keyPair.getPublic();
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
}

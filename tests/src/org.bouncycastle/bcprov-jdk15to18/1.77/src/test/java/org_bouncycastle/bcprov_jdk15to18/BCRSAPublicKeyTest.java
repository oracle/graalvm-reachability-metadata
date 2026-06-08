/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class BCRSAPublicKeyTest {
    private static final String BCRSA_PUBLIC_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey";

    @Test
    void javaSerializationPreservesPssPublicKeyAlgorithmIdentifier() throws Exception {
        BCRSAPublicKey publicKey = generatePssPublicKey();

        byte[] serialized = serialize(publicKey);
        BCRSAPublicKey restored = deserialize(serialized);

        assertEquals(BCRSA_PUBLIC_KEY_CLASS, publicKey.getClass().getName());
        assertEquals(BCRSA_PUBLIC_KEY_CLASS, restored.getClass().getName());
        assertEquals("RSASSA-PSS", publicKey.getAlgorithm());
        assertEquals("RSASSA-PSS", restored.getAlgorithm());
        assertEquals(publicKey.getModulus(), restored.getModulus());
        assertEquals(publicKey.getPublicExponent(), restored.getPublicExponent());
    }

    private static BCRSAPublicKey generatePssPublicKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
            "RSASSA-PSS",
            new BouncyCastleProvider());
        keyPairGenerator.initialize(512);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return (BCRSAPublicKey)keyPair.getPublic();
    }

    private static byte[] serialize(BCRSAPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCRSAPublicKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (BCRSAPublicKey)objectInputStream.readObject();
        }
    }
}

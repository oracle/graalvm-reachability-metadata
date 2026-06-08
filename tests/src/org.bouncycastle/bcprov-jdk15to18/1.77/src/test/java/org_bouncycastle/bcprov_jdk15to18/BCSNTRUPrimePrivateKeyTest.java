/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimeParameters;
import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimePrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.provider.ntruprime.BCSNTRUPrimePrivateKey;
import org.junit.jupiter.api.Test;

public class BCSNTRUPrimePrivateKeyTest {
    @Test
    void javaSerializationPreservesSntruPrimePrivateKeyEncoding() throws Exception {
        BCSNTRUPrimePrivateKey privateKey = createPrivateKey();

        byte[] serialized = serialize(privateKey);
        BCSNTRUPrimePrivateKey restored = deserialize(serialized);

        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(
                privateKey.getParameterSpec().getName(),
                restored.getParameterSpec().getName());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCSNTRUPrimePrivateKey createPrivateKey() throws Exception {
        BCSNTRUPrimePrivateKey encodedKeySource = new BCSNTRUPrimePrivateKey(
                createPrivateKeyParameters());
        KeyFactory keyFactory = KeyFactory.getInstance(
                "SNTRUPRIME",
                new BouncyCastlePQCProvider());
        PrivateKey privateKey = keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(encodedKeySource.getEncoded()));
        return (BCSNTRUPrimePrivateKey)privateKey;
    }

    private static SNTRUPrimePrivateKeyParameters createPrivateKeyParameters() {
        SNTRUPrimeParameters parameters = SNTRUPrimeParameters.sntrup653;
        return new SNTRUPrimePrivateKeyParameters(
                parameters,
                sequence((parameters.getP() + 3) / 4, 1),
                sequence((parameters.getP() + 3) / 4, 2),
                sequence(parameters.getPublicKeyBytes(), 3),
                sequence((parameters.getP() + 3) / 4, 4),
                sequence(32, 5));
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCSNTRUPrimePrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCSNTRUPrimePrivateKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (BCSNTRUPrimePrivateKey)objectInputStream.readObject();
        }
    }
}

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

import org.bouncycastle.pqc.crypto.frodo.FrodoParameters;
import org.bouncycastle.pqc.crypto.frodo.FrodoPrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.frodo.BCFrodoPrivateKey;
import org.junit.jupiter.api.Test;

public class BCFrodoPrivateKeyTest {
    @Test
    void javaSerializationPreservesFrodoPrivateKeyEncoding() throws Exception {
        BCFrodoPrivateKey privateKey = createPrivateKey();

        byte[] serialized = serialize(privateKey);
        BCFrodoPrivateKey restored = deserialize(serialized);

        assertEquals("Frodo", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(privateKey.getParameterSpec().getName(), restored.getParameterSpec().getName());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCFrodoPrivateKey createPrivateKey() {
        FrodoParameters parameters = FrodoParameters.frodokem640aes;
        byte[] privateKey = sequence(64, 1);
        return new BCFrodoPrivateKey(new FrodoPrivateKeyParameters(parameters, privateKey));
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCFrodoPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCFrodoPrivateKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (BCFrodoPrivateKey)objectInputStream.readObject();
        }
    }
}

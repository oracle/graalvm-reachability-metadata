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

import org.bouncycastle.pqc.crypto.cmce.CMCEParameters;
import org.bouncycastle.pqc.crypto.cmce.CMCEPrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.cmce.BCCMCEPrivateKey;
import org.junit.jupiter.api.Test;

public class BCCMCEPrivateKeyTest {
    @Test
    void javaSerializationPreservesCmcePrivateKeyEncoding() throws Exception {
        BCCMCEPrivateKey privateKey = createPrivateKey();

        byte[] serialized = serialize(privateKey);
        BCCMCEPrivateKey restored = deserialize(serialized);

        assertEquals("MCELIECE348864", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(privateKey.getParameterSpec().getName(), restored.getParameterSpec().getName());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCCMCEPrivateKey createPrivateKey() {
        CMCEParameters parameters = CMCEParameters.mceliece348864r3;
        int condBytes = (1 << (parameters.getM() - 4)) * (2 * parameters.getM() - 1);
        int privateKeySize = condBytes + parameters.getT() * 2 + parameters.getN() / 8 + 40;
        byte[] privateKey = sequence(privateKeySize, 1);
        return new BCCMCEPrivateKey(new CMCEPrivateKeyParameters(parameters, privateKey));
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCCMCEPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCCMCEPrivateKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (BCCMCEPrivateKey)objectInputStream.readObject();
        }
    }
}

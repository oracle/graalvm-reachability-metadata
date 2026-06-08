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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.pqc.crypto.bike.BIKEParameters;
import org.bouncycastle.pqc.crypto.bike.BIKEPrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.bike.BCBIKEPrivateKey;
import org.junit.jupiter.api.Test;

public class BCBIKEPrivateKeyTest {
    @Test
    void javaSerializationPreservesBikePrivateKeyEncoding() throws Exception {
        BCBIKEPrivateKey privateKey = createPrivateKey();

        byte[] serialized = serialize(privateKey);
        BCBIKEPrivateKey restored = deserialize(serialized);

        assertEquals("BIKE128", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(privateKey.getParameterSpec().getName(), restored.getParameterSpec().getName());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCBIKEPrivateKey createPrivateKey() {
        BIKEParameters parameters = BIKEParameters.bike128;
        byte[] h0 = sequence(parameters.getRByte(), 1);
        byte[] h1 = sequence(parameters.getRByte(), 17);
        byte[] sigma = sequence(parameters.getLByte(), 33);
        return new BCBIKEPrivateKey(new BIKEPrivateKeyParameters(parameters, h0, h1, sigma));
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCBIKEPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new EncodedPrivateKeyReplacingObjectOutputStream(
                byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCBIKEPrivateKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new EncodedPrivateKeyResolvingObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (BCBIKEPrivateKey)objectInputStream.readObject();
        }
    }

    private static final class EncodedPrivateKeyReplacingObjectOutputStream extends ObjectOutputStream {
        private boolean replacedEncodedKey;

        private EncodedPrivateKeyReplacingObjectOutputStream(ByteArrayOutputStream outputStream)
                throws IOException {
            super(outputStream);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (!replacedEncodedKey && object instanceof byte[]) {
                replacedEncodedKey = true;
                return new BCBIKEPrivateKey(PrivateKeyInfo.getInstance((byte[])object));
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedPrivateKeyResolvingObjectInputStream extends ObjectInputStream {
        private boolean resolvedNestedKey;

        private EncodedPrivateKeyResolvingObjectInputStream(ByteArrayInputStream inputStream)
                throws IOException {
            super(inputStream);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (!resolvedNestedKey && object instanceof BCBIKEPrivateKey) {
                resolvedNestedKey = true;
                return ((BCBIKEPrivateKey)object).getEncoded();
            }
            return super.resolveObject(object);
        }
    }
}

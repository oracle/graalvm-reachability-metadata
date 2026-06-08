/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumPrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.dilithium.BCDilithiumPrivateKey;
import org.junit.jupiter.api.Test;

public class BCDilithiumPrivateKeyTest {
    @Test
    void javaSerializationPreservesDilithiumPrivateKeyEncoding() throws Exception {
        BCDilithiumPrivateKey privateKey = createPrivateKey();

        byte[] serialized = serialize(privateKey);
        BCDilithiumPrivateKey restored = deserialize(serialized);

        assertEquals("DILITHIUM2", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(privateKey.getParameterSpec().getName(), restored.getParameterSpec().getName());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertArrayEquals(privateKey.getPublicKey().getEncoded(), restored.getPublicKey().getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCDilithiumPrivateKey createPrivateKey() {
        DilithiumParameters parameters = DilithiumParameters.dilithium2;
        return assertInstanceOf(BCDilithiumPrivateKey.class,
                new BCDilithiumPrivateKey(new DilithiumPrivateKeyParameters(
                        parameters,
                        sequence(32, 1),
                        sequence(32, 33),
                        sequence(64, 65),
                        sequence(384, 129),
                        sequence(384, 3),
                        sequence(1664, 11),
                        sequence(1280, 19))));
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCDilithiumPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedPrivateKeyReplacingObjectOutputStream objectOutputStream =
                new EncodedPrivateKeyReplacingObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
            assertEquals(1, objectOutputStream.replacedEncodedKeys);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCDilithiumPrivateKey deserialize(byte[] serialized) throws Exception {
        try (EncodedPrivateKeyResolvingObjectInputStream objectInputStream =
                new EncodedPrivateKeyResolvingObjectInputStream(new ByteArrayInputStream(serialized))) {
            BCDilithiumPrivateKey restored = (BCDilithiumPrivateKey)objectInputStream.readObject();
            assertEquals(1, objectInputStream.resolvedEncodedKeys);
            return restored;
        }
    }

    private static final class EncodedPrivateKeyReplacingObjectOutputStream extends ObjectOutputStream {
        private int replacedEncodedKeys;

        private EncodedPrivateKeyReplacingObjectOutputStream(ByteArrayOutputStream outputStream)
                throws IOException {
            super(outputStream);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (replacedEncodedKeys == 0 && object instanceof byte[]) {
                replacedEncodedKeys++;
                return new EncodedPrivateKey((byte[])object);
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedPrivateKeyResolvingObjectInputStream extends ObjectInputStream {
        private int resolvedEncodedKeys;

        private EncodedPrivateKeyResolvingObjectInputStream(ByteArrayInputStream inputStream)
                throws IOException {
            super(inputStream);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (object instanceof EncodedPrivateKey) {
                resolvedEncodedKeys++;
                return ((EncodedPrivateKey)object).encoded;
            }
            return super.resolveObject(object);
        }
    }

    private static final class EncodedPrivateKey implements Serializable {
        private static final long serialVersionUID = 1L;

        private final byte[] encoded;

        private EncodedPrivateKey(byte[] encoded) {
            this.encoded = encoded.clone();
        }
    }
}

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
import java.io.Serializable;

import org.bouncycastle.pqc.crypto.picnic.PicnicParameters;
import org.bouncycastle.pqc.crypto.picnic.PicnicPrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.picnic.BCPicnicPrivateKey;
import org.junit.jupiter.api.Test;

public class BCPicnicPrivateKeyTest {
    @Test
    void javaSerializationPreservesPicnicPrivateKeyEncoding() throws Exception {
        BCPicnicPrivateKey privateKey = createPrivateKey();

        byte[] serialized = serialize(privateKey);
        BCPicnicPrivateKey restored = deserialize(serialized);

        assertEquals("Picnic", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(privateKey.getParameterSpec().getName(), restored.getParameterSpec().getName());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCPicnicPrivateKey createPrivateKey() {
        return new BCPicnicPrivateKey(new PicnicPrivateKeyParameters(
                PicnicParameters.picnicl1fs,
                sequence(64, 1)));
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCPicnicPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new EncodedPrivateKeyReplacingObjectOutputStream(
                byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCPicnicPrivateKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new EncodedPrivateKeyResolvingObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (BCPicnicPrivateKey)objectInputStream.readObject();
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
                return new EncodedPrivateKeyCarrier((byte[])object);
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedPrivateKeyCarrier implements Serializable {
        private static final long serialVersionUID = 1L;

        private final byte[] encoded;

        private EncodedPrivateKeyCarrier(byte[] encoded) {
            this.encoded = encoded.clone();
        }

        private byte[] getEncoded() {
            return encoded.clone();
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
            if (!resolvedNestedKey && object instanceof EncodedPrivateKeyCarrier) {
                resolvedNestedKey = true;
                return ((EncodedPrivateKeyCarrier)object).getEncoded();
            }
            return super.resolveObject(object);
        }
    }
}

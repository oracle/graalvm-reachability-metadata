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
import java.security.PublicKey;

import org.bouncycastle.pqc.crypto.picnic.PicnicParameters;
import org.bouncycastle.pqc.crypto.picnic.PicnicPublicKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.picnic.BCPicnicPublicKey;
import org.junit.jupiter.api.Test;

public class BCPicnicPublicKeyTest {
    @Test
    void javaSerializationPreservesPicnicPublicKeyEncoding() throws Exception {
        BCPicnicPublicKey publicKey = createPublicKey();

        BCPicnicPublicKey restored = deserialize(serialize(publicKey));

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    @Test
    void javaSerializationPreservesPicnicPublicKeySerializedAsJcaPublicKey() throws Exception {
        BCPicnicPublicKey publicKey = createPublicKey();
        PublicKey jcaPublicKey = publicKey;

        BCPicnicPublicKey restored = deserialize(serialize(jcaPublicKey));

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    private static void assertPublicKeyRoundTrip(
            BCPicnicPublicKey publicKey,
            BCPicnicPublicKey restored) {
        assertEquals("Picnic", publicKey.getAlgorithm());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("X.509", restored.getFormat());
        assertEquals(publicKey.getParameterSpec().getName(), restored.getParameterSpec().getName());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
    }

    private static BCPicnicPublicKey createPublicKey() {
        return new BCPicnicPublicKey(new PicnicPublicKeyParameters(
                PicnicParameters.picnicl1fs,
                sequence(33, 17)));
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(PublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCPicnicPublicKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (BCPicnicPublicKey)objectInputStream.readObject();
        }
    }
}

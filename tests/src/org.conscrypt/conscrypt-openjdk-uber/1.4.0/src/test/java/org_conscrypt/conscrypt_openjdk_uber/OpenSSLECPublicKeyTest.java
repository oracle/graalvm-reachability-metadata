/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.interfaces.ECPublicKey;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class OpenSSLECPublicKeyTest {
    @Test
    void ecPublicKeySerializationRestoresEncodedKeyMaterial() throws Exception {
        Provider provider = Conscrypt.newProvider();
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", provider);
        keyPairGenerator.initialize(256);
        ECPublicKey publicKey = (ECPublicKey) keyPairGenerator.generateKeyPair().getPublic();

        byte[] serializedKey = serialize(publicKey);
        ECPublicKey restoredKey = assertInstanceOf(ECPublicKey.class, deserialize(serializedKey));

        assertEquals(publicKey.getAlgorithm(), restoredKey.getAlgorithm());
        assertEquals(publicKey.getFormat(), restoredKey.getFormat());
        assertEquals(publicKey.getW(), restoredKey.getW());
        assertArrayEquals(publicKey.getEncoded(), restoredKey.getEncoded());
        assertEquals(publicKey.getParams().getCurve(), restoredKey.getParams().getCurve());
        assertEquals(publicKey.getParams().getGenerator(), restoredKey.getParams().getGenerator());
        assertEquals(publicKey.getParams().getOrder(), restoredKey.getParams().getOrder());
        assertEquals(publicKey.getParams().getCofactor(), restoredKey.getParams().getCofactor());
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
            stream.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return stream.readObject();
        }
    }
}

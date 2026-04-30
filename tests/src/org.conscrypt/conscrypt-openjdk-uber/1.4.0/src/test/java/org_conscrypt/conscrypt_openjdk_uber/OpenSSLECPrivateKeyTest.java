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
import java.security.interfaces.ECPrivateKey;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class OpenSSLECPrivateKeyTest {
    @Test
    void ecPrivateKeySerializationRestoresEncodedKeyMaterial() throws Exception {
        Provider provider = Conscrypt.newProvider();
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", provider);
        keyPairGenerator.initialize(256);
        ECPrivateKey privateKey = (ECPrivateKey) keyPairGenerator.generateKeyPair().getPrivate();

        byte[] serializedKey = serialize(privateKey);
        ECPrivateKey restoredKey = assertInstanceOf(ECPrivateKey.class, deserialize(serializedKey));

        assertEquals(privateKey.getAlgorithm(), restoredKey.getAlgorithm());
        assertEquals(privateKey.getFormat(), restoredKey.getFormat());
        assertEquals(privateKey.getS(), restoredKey.getS());
        assertArrayEquals(privateKey.getEncoded(), restoredKey.getEncoded());
        assertEquals(privateKey.getParams().getCurve(), restoredKey.getParams().getCurve());
        assertEquals(privateKey.getParams().getGenerator(), restoredKey.getParams().getGenerator());
        assertEquals(privateKey.getParams().getOrder(), restoredKey.getParams().getOrder());
        assertEquals(privateKey.getParams().getCofactor(), restoredKey.getParams().getCofactor());
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

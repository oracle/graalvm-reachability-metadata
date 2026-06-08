/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jcajce.provider.symmetric.AES;
import org.junit.jupiter.api.Test;

public class BaseSecretKeyFactoryTest {
    private static final byte[] AES_KEY_BYTES = new byte[] {
        0x00, 0x01, 0x02, 0x03,
        0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0a, 0x0b,
        0x0c, 0x0d, 0x0e, 0x0f
    };

    @Test
    void getKeySpecInstantiatesRequestedByteArrayKeySpec() throws Exception {
        ExposedAesKeyFactory keyFactory = new ExposedAesKeyFactory();
        SecretKeySpec secretKey = new SecretKeySpec(AES_KEY_BYTES, "AES");

        X509EncodedKeySpec keySpec = (X509EncodedKeySpec)keyFactory.getKeySpec(
            secretKey, X509EncodedKeySpec.class);

        assertEquals("X.509", keySpec.getFormat());
        assertArrayEquals(AES_KEY_BYTES, keySpec.getEncoded());
    }

    private static final class ExposedAesKeyFactory extends AES.KeyFactory {
        KeySpec getKeySpec(SecretKey key, Class<?> keySpec) throws InvalidKeySpecException {
            return engineGetKeySpec(key, keySpec);
        }
    }
}

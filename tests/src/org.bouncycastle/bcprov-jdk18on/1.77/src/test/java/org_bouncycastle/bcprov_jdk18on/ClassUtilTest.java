/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class ClassUtilTest {
    @Test
    void loadsCipherServicesForPaddedAesEncryption() throws Exception {
        SecretKeySpec key = new SecretKeySpec(new byte[16], "AES");
        IvParameterSpec iv = new IvParameterSpec(new byte[16]);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding", KeyPairFixtures.BC);
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        byte[] plaintext = new byte[] {9, 8, 7};
        byte[] ciphertext = cipher.doFinal(plaintext);
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        assertArrayEquals(plaintext, cipher.doFinal(ciphertext));
    }
}

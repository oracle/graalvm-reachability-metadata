/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BaseBlockCipherInnerAEADGenericBlockCipherTest {
    @Test
    void authenticatesGcmAndRejectsAnAlteredTag() throws Exception {
        SecretKeySpec key = new SecretKeySpec(new byte[16], "AES");
        GCMParameterSpec parameters = new GCMParameterSpec(128, new byte[12]);
        byte[] plaintext = new byte[] {1, 2, 3, 4};
        Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding", KeyPairFixtures.BC);
        encrypt.init(Cipher.ENCRYPT_MODE, key, parameters);
        byte[] ciphertext = encrypt.doFinal(plaintext);
        Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding", KeyPairFixtures.BC);
        decrypt.init(Cipher.DECRYPT_MODE, key, parameters);
        assertArrayEquals(plaintext, decrypt.doFinal(ciphertext));
        ciphertext[ciphertext.length - 1] ^= 1;
        decrypt.init(Cipher.DECRYPT_MODE, key, parameters);
        assertThrows(AEADBadTagException.class, () -> decrypt.doFinal(ciphertext));
    }
}

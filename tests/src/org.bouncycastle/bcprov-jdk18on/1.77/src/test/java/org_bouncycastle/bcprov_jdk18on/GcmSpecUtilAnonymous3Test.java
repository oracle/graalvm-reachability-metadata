/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class GcmSpecUtilAnonymous3Test {
    @Test
    void extractsTagLengthAndNonceForAuthenticatedEncryption() throws Exception {
        SecretKeySpec key = new SecretKeySpec(new byte[16], "AES");
        GCMParameterSpec parameters = new GCMParameterSpec(96, new byte[12]);
        byte[] associatedData = new byte[] {11, 12};
        byte[] plaintext = new byte[] {21, 22, 23};
        Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding", KeyPairFixtures.BC);
        encrypt.init(Cipher.ENCRYPT_MODE, key, parameters);
        encrypt.updateAAD(associatedData);
        byte[] ciphertext = encrypt.doFinal(plaintext);
        Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding", KeyPairFixtures.BC);
        decrypt.init(Cipher.DECRYPT_MODE, key, parameters);
        decrypt.updateAAD(associatedData);
        assertArrayEquals(plaintext, decrypt.doFinal(ciphertext));
    }
}

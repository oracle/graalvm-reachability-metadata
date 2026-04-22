/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import java.nio.charset.StandardCharsets;
import java.security.Provider;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OpenSSLCipherEVP_AEADTest {

    private static final byte[] AES_KEY = new byte[] {
            0x00, 0x01, 0x02, 0x03,
            0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0A, 0x0B,
            0x0C, 0x0D, 0x0E, 0x0F
    };

    private static final byte[] IV = new byte[] {
            0x10, 0x11, 0x12, 0x13,
            0x14, 0x15, 0x16, 0x17,
            0x18, 0x19, 0x1A, 0x1B
    };

    @Test
    void throwsAeadBadTagExceptionWhenDecryptingTamperedGcmCiphertext() throws Exception {
        Provider provider = Conscrypt.newProvider();
        SecretKeySpec key = new SecretKeySpec(AES_KEY, "AES");
        GCMParameterSpec parameters = new GCMParameterSpec(128, IV);
        byte[] aad = "conscrypt-aead".getBytes(StandardCharsets.UTF_8);

        Cipher encryptCipher = Cipher.getInstance("AES/GCM/NoPadding", provider);
        encryptCipher.init(Cipher.ENCRYPT_MODE, key, parameters);
        encryptCipher.updateAAD(aad);

        byte[] ciphertext = encryptCipher.doFinal("tampered-payload".getBytes(StandardCharsets.UTF_8));
        ciphertext[ciphertext.length - 1] ^= 0x01;

        Cipher decryptCipher = Cipher.getInstance("AES/GCM/NoPadding", provider);
        decryptCipher.init(Cipher.DECRYPT_MODE, key, parameters);
        decryptCipher.updateAAD(aad);

        assertThatThrownBy(() -> decryptCipher.doFinal(ciphertext))
                .isInstanceOf(AEADBadTagException.class);
    }
}

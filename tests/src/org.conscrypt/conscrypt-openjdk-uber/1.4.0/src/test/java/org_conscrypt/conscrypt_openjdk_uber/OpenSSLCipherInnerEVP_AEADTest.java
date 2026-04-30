/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class OpenSSLCipherInnerEVP_AEADTest {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BITS = 128;

    @Test
    void gcmDecryptWithTamperedTagThrowsAeadBadTagException() throws Exception {
        Provider provider = Conscrypt.newProvider();
        SecretKeySpec key = new SecretKeySpec(new byte[] {
                0x00, 0x01, 0x02, 0x03,
                0x04, 0x05, 0x06, 0x07,
                0x08, 0x09, 0x0A, 0x0B,
                0x0C, 0x0D, 0x0E, 0x0F
        }, "AES");
        byte[] nonce = new byte[] {
                0x10, 0x11, 0x12, 0x13,
                0x14, 0x15, 0x16, 0x17,
                0x18, 0x19, 0x1A, 0x1B
        };
        byte[] aad = "authenticated but unencrypted".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "Conscrypt AEAD authentication failure".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = encrypt(provider, key, nonce, aad, plaintext);
        byte[] tamperedCiphertext = Arrays.copyOf(ciphertext, ciphertext.length);
        tamperedCiphertext[tamperedCiphertext.length - 1] ^= 0x01;

        Cipher decryptCipher = Cipher.getInstance(TRANSFORMATION, provider);
        decryptCipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
        decryptCipher.updateAAD(aad);

        AEADBadTagException exception = assertThrows(AEADBadTagException.class,
                () -> decryptCipher.doFinal(tamperedCiphertext));
        assertTrue(exception instanceof javax.crypto.BadPaddingException);
        assertEquals(provider.getName(), decryptCipher.getProvider().getName());
    }

    private static byte[] encrypt(Provider provider, SecretKeySpec key, byte[] nonce, byte[] aad,
            byte[] plaintext) throws Exception {
        Cipher encryptCipher = Cipher.getInstance(TRANSFORMATION, provider);
        encryptCipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
        encryptCipher.updateAAD(aad);
        return encryptCipher.doFinal(plaintext);
    }
}

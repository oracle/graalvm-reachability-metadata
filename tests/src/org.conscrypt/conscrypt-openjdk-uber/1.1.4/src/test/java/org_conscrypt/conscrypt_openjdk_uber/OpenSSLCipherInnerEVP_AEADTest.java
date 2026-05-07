/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class OpenSSLCipherInnerEVP_AEADTest {
    private static final String NATIVE_IMAGE_RUNTIME = "runtime";
    private static final byte[] KEY_BYTES = new byte[] {
            0x00, 0x01, 0x02, 0x03,
            0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0a, 0x0b,
            0x0c, 0x0d, 0x0e, 0x0f,
    };
    private static final byte[] IV_BYTES = new byte[] {
            0x10, 0x11, 0x12, 0x13,
            0x14, 0x15, 0x16, 0x17,
            0x18, 0x19, 0x1a, 0x1b,
    };
    private static final byte[] AAD_BYTES = "authenticated headers".getBytes(
            StandardCharsets.UTF_8);
    private static final byte[] PLAINTEXT_BYTES = "message protected by AES-GCM".getBytes(
            StandardCharsets.UTF_8);

    @Test
    void aesGcmDecryptWithCorruptedTagThrowsAeadBadTagException() throws Exception {
        Assumptions.assumeFalse(
                isNativeImageRuntime(),
                "Conscrypt providers must be registered during native image generation");

        Provider provider = Conscrypt.newProvider();
        SecretKeySpec key = new SecretKeySpec(KEY_BYTES, "AES");
        GCMParameterSpec parameters = new GCMParameterSpec(128, IV_BYTES);
        byte[] ciphertext = encrypt(provider, key, parameters);
        byte[] corruptedCiphertext = Arrays.copyOf(ciphertext, ciphertext.length);
        corruptedCiphertext[corruptedCiphertext.length - 1] ^= 0x01;

        Cipher decryptCipher = Cipher.getInstance("AES/GCM/NoPadding", provider);
        decryptCipher.init(Cipher.DECRYPT_MODE, key, parameters);
        decryptCipher.updateAAD(AAD_BYTES);

        assertThatThrownBy(() -> decryptCipher.doFinal(corruptedCiphertext))
                .isInstanceOf(AEADBadTagException.class);
    }

    private static byte[] encrypt(
            Provider provider, SecretKeySpec key, GCMParameterSpec parameters) throws Exception {
        Cipher encryptCipher = Cipher.getInstance("AES/GCM/NoPadding", provider);
        encryptCipher.init(Cipher.ENCRYPT_MODE, key, parameters);
        encryptCipher.updateAAD(AAD_BYTES);
        byte[] ciphertext = encryptCipher.doFinal(PLAINTEXT_BYTES);

        assertThat(ciphertext).hasSize(PLAINTEXT_BYTES.length + 16);
        return ciphertext;
    }

    private static boolean isNativeImageRuntime() {
        return NATIVE_IMAGE_RUNTIME.equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}

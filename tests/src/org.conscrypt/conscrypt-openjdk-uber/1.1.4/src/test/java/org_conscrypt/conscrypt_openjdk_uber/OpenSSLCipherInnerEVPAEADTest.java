/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.security.Provider;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class OpenSSLCipherInnerEVPAEADTest {
    private static final byte[] KEY_BYTES = new byte[] {
            0x00, 0x01, 0x02, 0x03,
            0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0A, 0x0B,
            0x0C, 0x0D, 0x0E, 0x0F,
    };
    private static final byte[] IV_BYTES = new byte[] {
            0x10, 0x11, 0x12, 0x13,
            0x14, 0x15, 0x16, 0x17,
            0x18, 0x19, 0x1A, 0x1B,
    };
    private static final byte[] AAD_BYTES = "authenticated headers"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] PLAINTEXT = "message protected by conscrypt aes-gcm"
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void gcmDecryptionWithModifiedTagThrowsAEADBadTagException() throws Exception {
        Conscrypt.checkAvailability();
        Provider provider = Conscrypt.newProvider();
        assertThat(Conscrypt.isConscrypt(provider)).isTrue();

        byte[] ciphertextAndTag = encryptWithAesGcm(provider);
        ciphertextAndTag[ciphertextAndTag.length - 1] ^= 0x01;

        Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding", provider);
        assertThat(decrypt.getProvider()).isSameAs(provider);
        decrypt.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY_BYTES, "AES"), parameters());
        decrypt.updateAAD(AAD_BYTES);

        assertThatExceptionOfType(AEADBadTagException.class)
                .isThrownBy(() -> decrypt.doFinal(ciphertextAndTag));
    }

    private static byte[] encryptWithAesGcm(Provider provider) throws Exception {
        Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding", provider);
        assertThat(encrypt.getProvider()).isSameAs(provider);
        encrypt.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY_BYTES, "AES"), parameters());
        encrypt.updateAAD(AAD_BYTES);
        return encrypt.doFinal(PLAINTEXT);
    }

    private static GCMParameterSpec parameters() {
        return new GCMParameterSpec(128, IV_BYTES);
    }
}

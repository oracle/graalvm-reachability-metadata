/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import java.nio.charset.StandardCharsets;
import java.security.Security;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClassUtilTest {
    private static final byte[] KEY = new byte[] {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
    };
    private static final byte[] NONCE = new byte[] {
        16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27
    };

    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void decryptsGcmDataAndReportsAnInvalidAuthenticationTag() throws Exception {
        SecretKeySpec key = new SecretKeySpec(KEY, "AES");
        GCMParameterSpec parameters = new GCMParameterSpec(128, NONCE);
        byte[] plaintext = "authenticated data".getBytes(StandardCharsets.UTF_8);

        Cipher encryptor = Cipher.getInstance("AES/GCM/NoPadding", BouncyCastleFipsProvider.PROVIDER_NAME);
        encryptor.init(Cipher.ENCRYPT_MODE, key, parameters);
        byte[] ciphertext = encryptor.doFinal(plaintext);

        Cipher decryptor = Cipher.getInstance("AES/GCM/NoPadding", BouncyCastleFipsProvider.PROVIDER_NAME);
        decryptor.init(Cipher.DECRYPT_MODE, key, parameters);
        assertThat(decryptor.doFinal(ciphertext)).isEqualTo(plaintext);

        ciphertext[ciphertext.length - 1] ^= 1;
        Cipher tamperedDecryptor = Cipher.getInstance("AES/GCM/NoPadding", BouncyCastleFipsProvider.PROVIDER_NAME);
        tamperedDecryptor.init(Cipher.DECRYPT_MODE, key, parameters);

        assertThatThrownBy(() -> tamperedDecryptor.doFinal(ciphertext))
            .isInstanceOf(AEADBadTagException.class);
    }
}

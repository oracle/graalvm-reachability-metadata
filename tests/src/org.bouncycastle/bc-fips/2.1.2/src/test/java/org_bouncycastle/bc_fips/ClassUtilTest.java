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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClassUtilTest {
    private static final byte[] KEY = new byte[16];
    private static final byte[] NONCE = new byte[] {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12
    };

    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void reportsAnInvalidGcmAuthenticationTag() throws Exception {
        SecretKeySpec key = new SecretKeySpec(KEY, "AES");
        GCMParameterSpec parameters = new GCMParameterSpec(128, NONCE);
        Cipher encryptor = Cipher.getInstance(
            "AES/GCM/NoPadding", BouncyCastleFipsProvider.PROVIDER_NAME);
        encryptor.init(Cipher.ENCRYPT_MODE, key, parameters);
        byte[] ciphertext = encryptor.doFinal(
            "authenticated message".getBytes(StandardCharsets.UTF_8));
        ciphertext[ciphertext.length - 1] ^= 1;

        Cipher decryptor = Cipher.getInstance(
            "AES/GCM/NoPadding", BouncyCastleFipsProvider.PROVIDER_NAME);
        decryptor.init(Cipher.DECRYPT_MODE, key, parameters);

        assertThatThrownBy(() -> decryptor.doFinal(ciphertext))
            .isInstanceOf(AEADBadTagException.class);
    }
}

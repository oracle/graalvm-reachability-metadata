/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import java.nio.charset.StandardCharsets;
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChaCha20SpecUtilAnonymous2Test {
    private static final byte[] KEY = new byte[32];
    private static final byte[] NONCE = new byte[] {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12
    };

    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void encryptsAndDecryptsWithAChaCha20ParameterSpec() throws Exception {
        byte[] plaintext = "ChaCha20 parameter reflection".getBytes(StandardCharsets.UTF_8);
        SecretKeySpec key = new SecretKeySpec(KEY, "ChaCha20");
        ChaCha20ParameterSpec parameters = new ChaCha20ParameterSpec(NONCE, 7);
        Cipher encryptor = Cipher.getInstance(
            "CHACHA20", BouncyCastleFipsProvider.PROVIDER_NAME);
        encryptor.init(Cipher.ENCRYPT_MODE, key, parameters);
        byte[] ciphertext = encryptor.doFinal(plaintext);

        Cipher decryptor = Cipher.getInstance(
            "CHACHA20", BouncyCastleFipsProvider.PROVIDER_NAME);
        decryptor.init(Cipher.DECRYPT_MODE, key, parameters);

        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(decryptor.doFinal(ciphertext)).isEqualTo(plaintext);
    }
}

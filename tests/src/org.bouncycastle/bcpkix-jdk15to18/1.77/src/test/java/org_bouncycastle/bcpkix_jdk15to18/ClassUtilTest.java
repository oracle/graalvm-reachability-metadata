/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk15to18;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.Provider;
import org.bouncycastle.its.jcajce.JceETSIDataEncryptor;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class ClassUtilTest {

    @Test
    void encryptsItsPayloadWithFreshCcmParameters() {
        Provider provider = new BouncyCastleProvider();
        JceETSIDataEncryptor encryptor = new JceETSIDataEncryptor.Builder()
                .setProvider(provider)
                .build();
        byte[] plaintext = "authenticated ITS payload".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = encryptor.encrypt(plaintext);

        assertThat(ciphertext).hasSize(plaintext.length + 16).isNotEqualTo(plaintext);
        assertThat(encryptor.getKey()).hasSize(16);
        assertThat(encryptor.getNonce()).hasSize(12);
    }
}

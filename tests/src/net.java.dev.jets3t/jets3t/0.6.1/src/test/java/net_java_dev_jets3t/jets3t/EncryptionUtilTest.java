/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import java.nio.charset.StandardCharsets;

import org.jets3t.service.security.EncryptionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EncryptionUtilTest {
    @Test
    public void encryptsAndDecryptsBytePayloadWithDefaultCipher() throws Exception {
        EncryptionUtil encryptionUtil = new EncryptionUtil("coverage-key");
        byte[] plaintext = "JetS3t encryption coverage".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = encryptionUtil.encrypt(plaintext);

        assertThat(encryptionUtil.getAlgorithm()).isEqualTo("PBEWithMD5AndDES");
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(encryptionUtil.decrypt(encrypted)).isEqualTo(plaintext);
        assertThat(encryptionUtil.decrypt(encrypted, 0, encrypted.length)).isEqualTo(plaintext);
    }

    @Test
    public void reportsDefaultCipherAvailability() {
        assertThat(EncryptionUtil.isCipherAvailableForUse("PBEWithMD5AndDES")).isTrue();
        assertThat(EncryptionUtil.listAvailablePbeCiphers(true))
            .anyMatch(cipher -> cipher.equalsIgnoreCase("PBEWithMD5AndDES"));
        assertThat(EncryptionUtil.listAvailableProviders()).isNotEmpty();
    }
}

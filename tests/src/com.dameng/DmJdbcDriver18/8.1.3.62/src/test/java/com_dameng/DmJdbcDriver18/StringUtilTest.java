/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_dameng.DmJdbcDriver18;

import dm.jdbc.util.StringUtil;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class StringUtilTest {
    @Test
    void encryptsAndDecryptsWithConfiguredEngine() {
        String plaintext = "database-password";
        String engine = CredentialCipher.class.getName();
        String encrypted = StringUtil.encrypt(plaintext, engine);
        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext, StringUtil.decrypt(encrypted, engine));
    }

    public static class CredentialCipher {
        private static final byte[] KEY = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

        public CredentialCipher() {
        }

        public String encrypt(String value) throws Exception {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = Arrays.copyOf(iv, iv.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(payload);
        }

        public String decrypt(String value) throws Exception {
            byte[] payload = Base64.getDecoder().decode(value);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, Arrays.copyOf(payload, 12)));
            return new String(cipher.doFinal(payload, 12, payload.length - 12), StandardCharsets.UTF_8);
        }
    }
}

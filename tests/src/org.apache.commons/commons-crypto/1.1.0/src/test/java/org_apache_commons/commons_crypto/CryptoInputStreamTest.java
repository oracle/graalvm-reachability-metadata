/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_crypto;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.crypto.cipher.CryptoCipherFactory;
import org.apache.commons.crypto.stream.CryptoInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CryptoInputStreamTest {
    @Test
    void decryptsWithJceCipherAndClosesDirectBuffers() throws Exception {
        byte[] plaintext = "commons-crypto-stream".getBytes(StandardCharsets.UTF_8);
        SecretKeySpec key = new SecretKeySpec("0123456789abcdef".getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec iv = new IvParameterSpec(new byte[16]);
        byte[] ciphertext = encrypt(plaintext, key, iv);
        Properties properties = new Properties();
        properties.setProperty(
                CryptoCipherFactory.CLASSES_KEY,
                CryptoCipherFactory.CipherProvider.JCE.getClassName());

        try (CryptoInputStream inputStream = new CryptoInputStream(
                "AES/CTR/NoPadding",
                properties,
                new ByteArrayInputStream(ciphertext),
                key,
                iv)) {
            assertThat(inputStream.readAllBytes()).isEqualTo(plaintext);
        }
    }

    private static byte[] encrypt(byte[] plaintext, SecretKeySpec key, IvParameterSpec iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        return cipher.doFinal(plaintext);
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.crypto.cipher.CryptoCipherFactory;
import org.apache.commons.crypto.stream.CryptoInputStream;
import org.junit.jupiter.api.Test;

public class CryptoInputStreamTest {
    private static final String TRANSFORMATION = "AES/CTR/NoPadding";
    private static final byte[] KEY_BYTES = {
            0x00, 0x01, 0x02, 0x03,
            0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0A, 0x0B,
            0x0C, 0x0D, 0x0E, 0x0F
    };
    private static final byte[] IV_BYTES = {
            0x10, 0x11, 0x12, 0x13,
            0x14, 0x15, 0x16, 0x17,
            0x18, 0x19, 0x1A, 0x1B,
            0x1C, 0x1D, 0x1E, 0x1F
    };

    @Test
    void closesStreamAfterDecryptingPayloadWithDirectBuffers() throws Exception {
        final SecretKeySpec key = new SecretKeySpec(KEY_BYTES, "AES");
        final IvParameterSpec iv = new IvParameterSpec(IV_BYTES);
        final byte[] plainText = repeatingPayload();
        final byte[] cipherText = encryptWithJce(plainText, key, iv);
        final CryptoInputStream inputStream = new CryptoInputStream(
                TRANSFORMATION, jceOnlyProperties(), new ByteArrayInputStream(cipherText), key, iv);

        try {
            final byte[] decrypted = inputStream.readAllBytes();

            assertThat(decrypted).isEqualTo(plainText);
            assertThat(inputStream.isOpen()).isTrue();
        } finally {
            inputStream.close();
        }

        assertThat(inputStream.isOpen()).isFalse();
        final IOException closedException = assertThrows(IOException.class, inputStream::read);
        assertThat(closedException).hasMessageContaining("Stream closed");
    }

    private static byte[] encryptWithJce(final byte[] plainText, final SecretKeySpec key, final IvParameterSpec iv)
            throws Exception {
        final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        return cipher.doFinal(plainText);
    }

    private static Properties jceOnlyProperties() {
        final Properties properties = new Properties();
        properties.setProperty(CryptoCipherFactory.CLASSES_KEY,
                CryptoCipherFactory.CipherProvider.JCE.getClassName());
        properties.setProperty(CryptoInputStream.STREAM_BUFFER_SIZE_KEY, "512");
        return properties;
    }

    private static byte[] repeatingPayload() {
        final byte[] payload = new byte[1537];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31 + 7);
        }
        return payload;
    }
}

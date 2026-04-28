/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_crypto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Properties;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.crypto.cipher.CryptoCipher;
import org.apache.commons.crypto.cipher.CryptoCipherFactory;
import org.apache.commons.crypto.stream.CryptoInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class CryptoInputStreamTest {
    @Test
    void decryptsCiphertextAndClosesDirectBuffers() throws Exception {
        byte[] plaintext = "commons-crypto-stream".getBytes(StandardCharsets.UTF_8);
        SecretKeySpec key = new SecretKeySpec("0123456789abcdef".getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec iv = new IvParameterSpec(new byte[16]);
        byte[] ciphertext = encrypt(plaintext, key, iv);

        try (CryptoInputStream inputStream = new CryptoInputStream(
                "AES/CTR/NoPadding",
                jceProperties(),
                new ByteArrayInputStream(ciphertext),
                key,
                iv)) {
            assertThat(inputStream.readAllBytes()).isEqualTo(plaintext);
            assertThat(inputStream.read()).isEqualTo(-1);
        }
    }

    @Test
    void closesDirectBuffersWhenUsingProtectedConstructor() throws Exception {
        TestCryptoInputStream inputStream = new TestCryptoInputStream();

        assertThat(inputStream.isOpen()).isTrue();
        assertThatCode(inputStream::close).doesNotThrowAnyException();
        assertThat(inputStream.isOpen()).isFalse();
    }

    private static byte[] encrypt(byte[] plaintext, SecretKeySpec key, IvParameterSpec iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        return cipher.doFinal(plaintext);
    }

    private static Properties jceProperties() {
        Properties properties = new Properties();
        properties.setProperty(
                CryptoCipherFactory.CLASSES_KEY,
                CryptoCipherFactory.CipherProvider.JCE.getClassName());
        return properties;
    }

    private static final class TestCryptoInputStream extends CryptoInputStream {
        private TestCryptoInputStream() throws IOException {
            super(
                    new ByteArrayInputStream(new byte[0]),
                    new PassthroughCryptoCipher(),
                    512,
                    new SecretKeySpec(new byte[16], "AES"),
                    new IvParameterSpec(new byte[16]));
        }
    }

    private static final class PassthroughCryptoCipher implements CryptoCipher {
        @Override
        public int getBlockSize() {
            return 16;
        }

        @Override
        public String getAlgorithm() {
            return "AES/CTR/NoPadding";
        }

        @Override
        public void init(int mode, Key key, AlgorithmParameterSpec params)
                throws InvalidKeyException, InvalidAlgorithmParameterException {
        }

        @Override
        public int update(ByteBuffer inBuffer, ByteBuffer outBuffer) {
            int length = Math.min(inBuffer.remaining(), outBuffer.remaining());
            for (int i = 0; i < length; i++) {
                outBuffer.put(inBuffer.get());
            }
            return length;
        }

        @Override
        public int update(byte[] input, int inputOffset, int inputLength, byte[] output, int outputOffset)
                throws ShortBufferException {
            int length = Math.min(inputLength, output.length - outputOffset);
            System.arraycopy(input, inputOffset, output, outputOffset, length);
            return length;
        }

        @Override
        public int doFinal(ByteBuffer inBuffer, ByteBuffer outBuffer)
                throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
            return update(inBuffer, outBuffer);
        }

        @Override
        public int doFinal(byte[] input, int inputOffset, int inputLength, byte[] output, int outputOffset)
                throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
            return update(input, inputOffset, inputLength, output, outputOffset);
        }

        @Override
        public void close() {
        }
    }
}

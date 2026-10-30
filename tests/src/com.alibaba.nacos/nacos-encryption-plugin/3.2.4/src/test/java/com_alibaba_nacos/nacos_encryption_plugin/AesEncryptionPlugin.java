/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_encryption_plugin;

import com.alibaba.nacos.plugin.encryption.spi.EncryptionPluginService;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesEncryptionPlugin implements EncryptionPluginService {
    private static final String WRAPPING_KEY = newSecretKey();
    private final String algorithm;

    public AesEncryptionPlugin() {
        this("TestAes");
    }

    public AesEncryptionPlugin(String algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public String algorithmName() {
        return algorithm;
    }

    @Override
    public String generateSecretKey() {
        return newSecretKey();
    }

    @Override
    public String encrypt(String secretKey, String content) {
        try {
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = newCipher(Cipher.ENCRYPT_MODE, secretKey, nonce);
            byte[] ciphertext = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot encrypt configuration", e);
        }
    }

    @Override
    public String decrypt(String secretKey, String content) {
        try {
            ByteBuffer envelope = ByteBuffer.wrap(Base64.getDecoder().decode(content));
            byte[] nonce = new byte[12];
            envelope.get(nonce);
            byte[] ciphertext = new byte[envelope.remaining()];
            envelope.get(ciphertext);
            return new String(newCipher(Cipher.DECRYPT_MODE, secretKey, nonce).doFinal(ciphertext),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot decrypt configuration", e);
        }
    }

    @Override
    public String encryptSecretKey(String secretKey) {
        return encrypt(WRAPPING_KEY, secretKey);
    }

    @Override
    public String decryptSecretKey(String secretKey) {
        return decrypt(WRAPPING_KEY, secretKey);
    }

    private static String newSecretKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(128);
            return Base64.getEncoder().encodeToString(generator.generateKey().getEncoded());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot generate encryption key", e);
        }
    }

    private static Cipher newCipher(int mode, String secretKey, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(Base64.getDecoder().decode(secretKey), "AES"),
                new GCMParameterSpec(128, nonce));
        return cipher;
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.jce.MD5;
import com.jcraft.jsch.jce.Random;
import com.jcraft.jsch.jce.TripleDESCBC;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class KeyPairTest {
    private static final String PASSPHRASE = "encrypted-key-passphrase";
    private static final String UNENCRYPTED_RSA_PRIVATE_KEY = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIBOQIBAAJBANiTsnMzSUqDH6OsCzp8MwOAoDqLMKWs/2RaF8LCEU8x6GtUCl4Z
            rzCPeTtpYrk4jBCT6nk/gl7DMuo/A23C+R8CAwEAAQJAbjC3WmV9pKuSLXQbQmZu
            jhmjbCXlWmXqiuRLsouKwv9x33o/MSiDNNbsDtf+DRSeHmEG57NT3rvbcwT9UQXQ
            AQIhAO8SkD2uBP1JfBlcCy470MQ3nVBBN6eAuvGTNg2Fv89hAiEA5+lgzNiPIjMJ
            S2/MK9ohLyIu0qiY10oC5RhiBHpqGH8CIHIOJyZl+RdlkYD/uo3KF6Uk6zY4hvaw
            oX9SvfzhjErBAiBr0K/UDAnfGGMF0x/Uc0BiLT4faYpE7H+UClXnRxHz+QIgB/Vo
            pgXfgYJiRRW8SFPhI5GJRCtzzm9kuazsGtJt0x4=
            -----END RSA PRIVATE KEY-----
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesAndDecryptsEncryptedRsaPrivateKey() throws Exception {
        keepConfiguredImplementationsReachable();
        configureKeyPairImplementations(
                ConfiguredTripleDESCBC.class.getName(), ConfiguredMD5.class.getName(), ConfiguredRandom.class.getName());
        try {
            Path unencryptedPrivateKeyPath = temporaryDirectory.resolve("unencrypted-rsa-key");
            Files.writeString(unencryptedPrivateKeyPath, UNENCRYPTED_RSA_PRIVATE_KEY, StandardCharsets.US_ASCII);

            KeyPair keyPair = KeyPair.load(new JSch(), unencryptedPrivateKeyPath.toString());
            assertThat(keyPair.isEncrypted()).isFalse();
            keyPair.setPassphrase(PASSPHRASE);

            ByteArrayOutputStream privateKeyOutputStream = new ByteArrayOutputStream();
            keyPair.writePrivateKey(privateKeyOutputStream);
            byte[] privateKeyBytes = privateKeyOutputStream.toByteArray();
            String privateKey = new String(privateKeyBytes, StandardCharsets.US_ASCII);
            assertThat(privateKey)
                    .contains("-----BEGIN RSA PRIVATE KEY-----")
                    .contains("Proc-Type: 4,ENCRYPTED")
                    .contains("DEK-Info: DES-EDE3-CBC,")
                    .contains("-----END RSA PRIVATE KEY-----");

            Path encryptedPrivateKeyPath = temporaryDirectory.resolve("encrypted-rsa-key");
            Files.write(encryptedPrivateKeyPath, privateKeyBytes);
            KeyPair loadedKeyPair = KeyPair.load(new JSch(), encryptedPrivateKeyPath.toString());

            assertThat(loadedKeyPair.isEncrypted()).isTrue();
            assertThat(loadedKeyPair.decrypt(PASSPHRASE)).isTrue();
            assertThat(loadedKeyPair.isEncrypted()).isFalse();
            assertThat(loadedKeyPair.getFingerPrint()).isNotBlank();
        } finally {
            configureKeyPairImplementations(
                    TripleDESCBC.class.getName(), MD5.class.getName(), Random.class.getName());
        }
    }

    private static void configureKeyPairImplementations(String cipher, String hash, String random) {
        Hashtable<String, String> config = new Hashtable<>();
        config.put("3des-cbc", cipher);
        config.put("md5", hash);
        config.put("random", random);
        JSch.setConfig(config);
    }

    private static void keepConfiguredImplementationsReachable() {
        assertThat(new ConfiguredTripleDESCBC()).isNotNull();
        assertThat(new ConfiguredMD5()).isNotNull();
        assertThat(new ConfiguredRandom()).isNotNull();
    }

    public static final class ConfiguredTripleDESCBC extends TripleDESCBC {
    }

    public static final class ConfiguredMD5 extends MD5 {
    }

    public static final class ConfiguredRandom extends Random {
    }
}

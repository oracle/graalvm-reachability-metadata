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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import org.junit.jupiter.api.Test;

public class KeyPairRSATest {
    private static final String DEFAULT_RSA_KEY_PAIR_GENERATOR = "com.jcraft.jsch.jce.KeyPairGenRSA";

    @Test
    void generatesAndWritesRsaKeyPairWithConfiguredJceGenerator() throws Exception {
        configureRsaKeyPairGenerator(DEFAULT_RSA_KEY_PAIR_GENERATOR);

        KeyPair keyPair = KeyPair.genKeyPair(new JSch(), KeyPair.RSA, 1024);

        assertThat(keyPair.getKeyType()).isEqualTo(KeyPair.RSA);

        ByteArrayOutputStream privateKeyOutputStream = new ByteArrayOutputStream();
        keyPair.writePrivateKey(privateKeyOutputStream);
        String privateKey = privateKeyOutputStream.toString(StandardCharsets.US_ASCII);
        assertThat(privateKey)
                .contains("-----BEGIN RSA PRIVATE KEY-----")
                .contains("-----END RSA PRIVATE KEY-----");

        ByteArrayOutputStream publicKeyOutputStream = new ByteArrayOutputStream();
        keyPair.writePublicKey(publicKeyOutputStream, "coverage");
        String publicKey = publicKeyOutputStream.toString(StandardCharsets.US_ASCII);
        assertThat(publicKey).startsWith("ssh-rsa ").endsWith(" coverage\n");
    }

    private static void configureRsaKeyPairGenerator(String keyPairGenerator) {
        Hashtable<String, String> config = new Hashtable<>();
        config.put("keypairgen.rsa", keyPairGenerator);
        JSch.setConfig(config);
    }
}

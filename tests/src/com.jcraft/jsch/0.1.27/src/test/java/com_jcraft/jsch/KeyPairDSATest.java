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
import com.jcraft.jsch.KeyPairGenDSA;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.DSAKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.util.Hashtable;
import org.junit.jupiter.api.Test;

public class KeyPairDSATest {
    @Test
    void generatesAndWritesDsaKeyPair() throws Exception {
        keepConfiguredDsaKeyPairGeneratorReachable();
        configureDsaKeyPairGenerator(ConfiguredKeyPairGenDSA.class.getName());

        KeyPair keyPair = KeyPair.genKeyPair(new JSch(), KeyPair.DSA, 1024);

        assertThat(keyPair.getKeyType()).isEqualTo(KeyPair.DSA);

        ByteArrayOutputStream privateKeyOutputStream = new ByteArrayOutputStream();
        keyPair.writePrivateKey(privateKeyOutputStream);
        String privateKey = privateKeyOutputStream.toString(StandardCharsets.US_ASCII);
        assertThat(privateKey)
                .contains("-----BEGIN DSA PRIVATE KEY-----")
                .contains("-----END DSA PRIVATE KEY-----");

        ByteArrayOutputStream publicKeyOutputStream = new ByteArrayOutputStream();
        keyPair.writePublicKey(publicKeyOutputStream, "coverage");
        String publicKey = publicKeyOutputStream.toString(StandardCharsets.US_ASCII);
        assertThat(publicKey).startsWith("ssh-dss ").endsWith(" coverage\n");
    }

    private static void configureDsaKeyPairGenerator(String keyPairGenerator) {
        Hashtable<String, String> config = new Hashtable<>();
        config.put("keypairgen.dsa", keyPairGenerator);
        JSch.setConfig(config);
    }

    private static void keepConfiguredDsaKeyPairGeneratorReachable() {
        assertThat(new ConfiguredKeyPairGenDSA()).isNotNull();
    }

    public static final class ConfiguredKeyPairGenDSA implements KeyPairGenDSA {
        private byte[] privateKey;
        private byte[] publicKey;
        private byte[] p;
        private byte[] q;
        private byte[] g;

        @Override
        public void init(int keySize) throws Exception {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DSA");
            keyPairGenerator.initialize(keySize, new SecureRandom());
            java.security.KeyPair generatedKeyPair = keyPairGenerator.generateKeyPair();
            PublicKey generatedPublicKey = generatedKeyPair.getPublic();
            PrivateKey generatedPrivateKey = generatedKeyPair.getPrivate();

            privateKey = ((DSAPrivateKey) generatedPrivateKey).getX().toByteArray();
            publicKey = ((DSAPublicKey) generatedPublicKey).getY().toByteArray();

            DSAParams parameters = ((DSAKey) generatedPrivateKey).getParams();
            p = parameters.getP().toByteArray();
            q = parameters.getQ().toByteArray();
            g = parameters.getG().toByteArray();
        }

        @Override
        public byte[] getX() {
            return privateKey;
        }

        @Override
        public byte[] getY() {
            return publicKey;
        }

        @Override
        public byte[] getP() {
            return p;
        }

        @Override
        public byte[] getQ() {
            return q;
        }

        @Override
        public byte[] getG() {
            return g;
        }
    }
}

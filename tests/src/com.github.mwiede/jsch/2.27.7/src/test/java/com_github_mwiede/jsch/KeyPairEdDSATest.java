/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_mwiede.jsch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Signature;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class KeyPairEdDSATest {
    private static final byte[] MESSAGE =
            "message signed by an EdDSA SSH key".getBytes(StandardCharsets.UTF_8);

    @Test
    void generatedEd25519KeySignsAndVerifiesMessage() throws Exception {
        JSch jsch = new JSch();
        KeyPair keyPair = KeyPair.genKeyPair(jsch, KeyPair.ED25519);

        byte[] signature = keyPair.getSignature(MESSAGE);
        Signature verifier = keyPair.getVerifier();
        verifier.update(MESSAGE);

        assertThat(keyPair.getKeyType()).isEqualTo(KeyPair.ED25519);
        assertThat(keyPair.getPublicKeyBlob()).isNotEmpty();
        assertThat(signature).isNotEmpty();
        assertThat(verifier.verify(signature)).isTrue();
    }

    @Test
    void pkcs8Ed25519PrivateKeyAttemptsConfiguredFromPrivateGenerator() {
        JSch jsch = new JSch();
        String previousGenerator = JSch.getConfig("keypairgen_fromprivate.eddsa");
        JSch.setConfig("keypairgen_fromprivate.eddsa", "com.jcraft.jsch.jce.KeyPairGenEdDSA");
        try {
            assertThatThrownBy(() -> KeyPair.load(jsch, pkcs8Ed25519PrivateKey(), null))
                    .isInstanceOf(JSchException.class)
                    .hasMessageContaining("invalid privatekey");
        } finally {
            JSch.setConfig("keypairgen_fromprivate.eddsa", previousGenerator);
        }
    }

    private static byte[] pkcs8Ed25519PrivateKey() {
        byte[] privateKeyInfo =
                Base64.getMimeEncoder(64, new byte[] {'\n'}).encode(eddsaPkcs8Der());
        String pem = """
                -----BEGIN PRIVATE KEY-----
                %s
                -----END PRIVATE KEY-----
                """.formatted(new String(privateKeyInfo, StandardCharsets.US_ASCII));
        return pem.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] eddsaPkcs8Der() {
        return new byte[] {
                0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06,
                0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20,
                0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef,
                0x10, 0x32, 0x54, 0x76, (byte) 0x98, (byte) 0xba, (byte) 0xdc, (byte) 0xfe,
                0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x00,
                0x01, 0x13, 0x25, 0x37, 0x49, 0x5b, 0x6d, 0x7f
        };
    }
}

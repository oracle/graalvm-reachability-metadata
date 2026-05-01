/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_mwiede.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Signature;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class KeyPairRSATest {
    private static final byte[] MESSAGE =
            "message signed by an RSA SSH key".getBytes(StandardCharsets.UTF_8);

    @Test
    void generatedRsaKeySignsAndVerifiesMessagesForSupportedAlgorithms() throws Exception {
        JSch jsch = new JSch();
        KeyPair keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 1024);
        String[] algorithms = {"ssh-rsa", "rsa-sha2-256", "rsa-sha2-512"};

        try {
            assertThat(keyPair.getKeyType()).isEqualTo(KeyPair.RSA);
            assertThat(keyPair.getKeySize()).isEqualTo(1024);
            assertThat(keyPair.getPublicKeyBlob()).isNotEmpty();

            for (String algorithm : algorithms) {
                byte[] signature = keyPair.getSignature(MESSAGE, algorithm);
                Signature verifier = keyPair.getVerifier(algorithm);
                verifier.update(MESSAGE);

                assertThat(signature).isNotEmpty();
                assertThat(verifier.verify(signature)).isTrue();
            }
        } finally {
            keyPair.dispose();
        }
    }
}

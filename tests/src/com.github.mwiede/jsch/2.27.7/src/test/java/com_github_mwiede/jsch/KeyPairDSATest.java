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

public class KeyPairDSATest {
    private static final byte[] MESSAGE =
            "message signed by a DSA SSH key".getBytes(StandardCharsets.UTF_8);

    @Test
    void generatedDsaKeySignsAndVerifiesMessage() throws Exception {
        JSch jsch = new JSch();
        KeyPair keyPair = KeyPair.genKeyPair(jsch, KeyPair.DSA, 1024);
        try {
            byte[] signature = keyPair.getSignature(MESSAGE);
            Signature verifier = keyPair.getVerifier();
            verifier.update(MESSAGE);

            assertThat(keyPair.getKeyType()).isEqualTo(KeyPair.DSA);
            assertThat(keyPair.getKeySize()).isEqualTo(1024);
            assertThat(keyPair.getPublicKeyBlob()).isNotEmpty();
            assertThat(signature).isNotEmpty();
            assertThat(verifier.verify(signature)).isTrue();
        } finally {
            keyPair.dispose();
        }
    }
}

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

public class KeyPairECDSATest {
    private static final byte[] MESSAGE =
            "message signed by an ECDSA SSH key".getBytes(StandardCharsets.UTF_8);

    @Test
    void generatedEcdsaKeysSignAndVerifyMessagesForSupportedCurves() throws Exception {
        JSch jsch = new JSch();
        int[] keySizes = {256, 384, 521};

        for (int keySize : keySizes) {
            KeyPair keyPair = KeyPair.genKeyPair(jsch, KeyPair.ECDSA, keySize);
            try {
                byte[] signature = keyPair.getSignature(MESSAGE);
                Signature verifier = keyPair.getVerifier();
                verifier.update(MESSAGE);

                assertThat(keyPair.getKeyType()).isEqualTo(KeyPair.ECDSA);
                assertThat(keyPair.getKeySize()).isEqualTo(keySize);
                assertThat(keyPair.getPublicKeyBlob()).isNotEmpty();
                assertThat(signature).isNotEmpty();
                assertThat(verifier.verify(signature)).isTrue();
            } finally {
                keyPair.dispose();
            }
        }
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.Buffer;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class KeyPairRSATest {
    @Test
    void generatesRsaKeyWithRequestedSizeAndExportsPublicKey() throws Exception {
        KeyPair pair = KeyPair.genKeyPair(new JSch(), KeyPair.RSA, 2048);
        try {
            assertThat(pair.getKeyType()).isEqualTo(KeyPair.RSA);
            Buffer blob = new Buffer(pair.getPublicKeyBlob());
            assertThat(new String(blob.getString(), StandardCharsets.US_ASCII)).isEqualTo("ssh-rsa");
            assertThat(new BigInteger(1, blob.getString())).isEqualTo(BigInteger.valueOf(65537));
            assertThat(new BigInteger(1, blob.getString()).bitLength()).isEqualTo(2048);
            ByteArrayOutputStream publicKey = new ByteArrayOutputStream();
            pair.writePublicKey(publicKey, "rsa-user");
            assertThat(publicKey.toString(StandardCharsets.US_ASCII))
                    .startsWith("ssh-rsa ").endsWith(" rsa-user\n");
        } finally {
            pair.dispose();
        }
    }
}

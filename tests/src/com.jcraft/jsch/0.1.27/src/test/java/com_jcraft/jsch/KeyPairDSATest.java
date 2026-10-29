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

public class KeyPairDSATest {
    @Test
    void generatesDsaParametersAndExportsPublicKey() throws Exception {
        KeyPair pair = KeyPair.genKeyPair(new JSch(), KeyPair.DSA, 1024);
        try {
            assertThat(pair.getKeyType()).isEqualTo(KeyPair.DSA);
            Buffer blob = new Buffer(pair.getPublicKeyBlob());
            assertThat(new String(blob.getString(), StandardCharsets.US_ASCII)).isEqualTo("ssh-dss");
            BigInteger prime = new BigInteger(1, blob.getString());
            BigInteger subgroup = new BigInteger(1, blob.getString());
            BigInteger generator = new BigInteger(1, blob.getString());
            BigInteger publicValue = new BigInteger(1, blob.getString());
            assertThat(prime.bitLength()).isEqualTo(1024);
            assertThat(subgroup.bitLength()).isEqualTo(160);
            assertThat(generator.modPow(subgroup, prime)).isEqualTo(BigInteger.ONE);
            assertThat(publicValue.modPow(subgroup, prime)).isEqualTo(BigInteger.ONE);
            ByteArrayOutputStream publicKey = new ByteArrayOutputStream();
            pair.writePublicKey(publicKey, "dsa-user");
            assertThat(publicKey.toString(StandardCharsets.US_ASCII))
                    .startsWith("ssh-dss ").endsWith(" dsa-user\n");
        } finally {
            pair.dispose();
        }
    }
}

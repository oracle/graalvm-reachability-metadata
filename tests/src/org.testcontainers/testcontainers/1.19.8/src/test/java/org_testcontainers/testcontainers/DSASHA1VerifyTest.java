/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.trilead.ssh2.signature.DSAPublicKey;
import org.testcontainers.shaded.com.trilead.ssh2.signature.DSASHA1Verify;

import static org.assertj.core.api.Assertions.assertThat;

public class DSASHA1VerifyTest {
    @Test
    void roundTripsSshDsaPublicKeyEncoding() throws Exception {
        DSAPublicKey key = new DSAPublicKey(
            BigInteger.valueOf(23),
            BigInteger.valueOf(11),
            BigInteger.valueOf(2),
            BigInteger.valueOf(8)
        );

        DSAPublicKey decoded = DSASHA1Verify.decodeSSHDSAPublicKey(DSASHA1Verify.encodeSSHDSAPublicKey(key));

        assertThat(decoded.getP()).isEqualTo(key.getP());
        assertThat(decoded.getY()).isEqualTo(key.getY());
    }
}

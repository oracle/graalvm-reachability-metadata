/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.trilead.ssh2.signature.RSAPublicKey;
import org.testcontainers.shaded.com.trilead.ssh2.signature.RSASHA1Verify;

import static org.assertj.core.api.Assertions.assertThat;

public class RSASHA1VerifyTest {
    @Test
    void roundTripsSshRsaPublicKeyEncoding() throws Exception {
        RSAPublicKey key = new RSAPublicKey(BigInteger.valueOf(17), BigInteger.valueOf(3233));

        RSAPublicKey decoded = RSASHA1Verify.decodeSSHRSAPublicKey(RSASHA1Verify.encodeSSHRSAPublicKey(key));

        assertThat(decoded.getE()).isEqualTo(key.getE());
        assertThat(decoded.getN()).isEqualTo(key.getN());
    }
}

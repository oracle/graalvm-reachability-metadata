/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.security.Provider;
import java.security.SecureRandom;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.jce.provider.BouncyCastleProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class DRBGAnonymous4Test {
    @Test
    void createsNonceBytesFromTheProviderDrbg() throws Exception {
        Provider provider = new BouncyCastleProvider();
        SecureRandom random = SecureRandom.getInstance("NONCEANDIV", provider);
        byte[] first = new byte[16];
        byte[] second = new byte[16];

        random.nextBytes(first);
        random.nextBytes(second);

        assertThat(first).isNotEqualTo(second);
    }
}

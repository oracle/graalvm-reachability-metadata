/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class DRBGAnonymous4Test {
    @Test
    void obtainsStrongEntropyForNonceGeneration() throws Exception {
        String previous = Security.getProperty("securerandom.source");
        try {
            Security.setProperty("securerandom.source", "");
            SecureRandom random = SecureRandom.getInstance("NONCEANDIV", KeyPairFixtures.BC);
            byte[] first = new byte[32];
            byte[] second = new byte[32];
            random.nextBytes(first);
            random.nextBytes(second);
            assertFalse(Arrays.equals(first, second));
        } finally {
            Security.setProperty("securerandom.source", previous == null ? "" : previous);
        }
    }
}

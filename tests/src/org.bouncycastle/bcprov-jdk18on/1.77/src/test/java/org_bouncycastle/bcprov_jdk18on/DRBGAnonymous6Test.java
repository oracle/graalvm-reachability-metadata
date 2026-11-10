/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.security.SecureRandom;
import java.util.Arrays;

import org.bouncycastle.crypto.prng.BasicEntropySourceProvider;
import org.bouncycastle.crypto.prng.EntropySource;
import org.bouncycastle.crypto.prng.EntropySourceProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class DRBGAnonymous6Test {
    @Test
    void loadsConfiguredEntropySourceForDefaultRandom() throws Exception {
        String property = "org.bouncycastle.drbg.entropysource";
        String previous = System.getProperty(property);
        try {
            System.setProperty(property, ApplicationEntropySource.class.getName());
            SecureRandom random = SecureRandom.getInstance("DEFAULT", KeyPairFixtures.BC);
            byte[] first = new byte[32];
            byte[] second = new byte[32];
            random.nextBytes(first);
            random.nextBytes(second);
            assertFalse(Arrays.equals(first, second));
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    public static class ApplicationEntropySource implements EntropySourceProvider {
        private final EntropySourceProvider delegate = new BasicEntropySourceProvider(new SecureRandom(), true);

        public ApplicationEntropySource() {
        }

        @Override
        public EntropySource get(int bitsRequired) {
            return delegate.get(bitsRequired);
        }
    }
}

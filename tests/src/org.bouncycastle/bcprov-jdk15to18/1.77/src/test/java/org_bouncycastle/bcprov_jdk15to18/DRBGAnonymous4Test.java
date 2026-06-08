/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Provider;
import java.security.SecureRandomSpi;
import java.security.Security;
import java.util.concurrent.atomic.AtomicInteger;

import org.bouncycastle.jcajce.provider.drbg.DRBG;
import org.junit.jupiter.api.Test;

public class DRBGAnonymous4Test {
    private static final String ENTROPY_SOURCE_PROPERTY = "org.bouncycastle.drbg.entropysource";
    private static final String SECURITY_RANDOM_SOURCE_PROPERTY = "securerandom.source";
    private static final String STRONG_ALGORITHMS_PROPERTY = "securerandom.strongAlgorithms";

    @Test
    void nonceAndIvSecureRandomSpiUsesStrongPlatformEntropySource() throws Exception {
        Provider strongProvider = new StrongSecureRandomProvider();
        Provider previousProvider = Security.getProvider(strongProvider.getName());
        String previousEntropySource = System.getProperty(ENTROPY_SOURCE_PROPERTY);
        String previousRandomSource = Security.getProperty(SECURITY_RANDOM_SOURCE_PROPERTY);
        String previousStrongAlgorithms = Security.getProperty(STRONG_ALGORITHMS_PROPERTY);
        StrongSecureRandomSpi.seedRequests.set(0);

        if (previousProvider != null) {
            Security.removeProvider(previousProvider.getName());
        }
        Security.insertProviderAt(strongProvider, 1);
        System.clearProperty(ENTROPY_SOURCE_PROPERTY);
        Security.setProperty(SECURITY_RANDOM_SOURCE_PROPERTY, "not-a-url");
        Security.setProperty(STRONG_ALGORITHMS_PROPERTY, StrongSecureRandomProvider.ALGORITHM + ":"
            + StrongSecureRandomProvider.NAME);

        try {
            ExposedNonceAndIv nonceAndIv = new ExposedNonceAndIv();
            byte[] nonceBytes = new byte[32];

            nonceAndIv.nextBytes(nonceBytes);

            assertEquals(32, nonceBytes.length);
            assertTrue(StrongSecureRandomSpi.seedRequests.get() > 0);
        } finally {
            Security.removeProvider(strongProvider.getName());
            if (previousProvider != null) {
                Security.addProvider(previousProvider);
            }
            restoreSystemProperty(ENTROPY_SOURCE_PROPERTY, previousEntropySource);
            restoreSecurityProperty(SECURITY_RANDOM_SOURCE_PROPERTY, previousRandomSource);
            restoreSecurityProperty(STRONG_ALGORITHMS_PROPERTY, previousStrongAlgorithms);
        }
    }

    private static final class ExposedNonceAndIv extends DRBG.NonceAndIV {
        private void nextBytes(byte[] bytes) {
            engineNextBytes(bytes);
        }
    }

    public static final class StrongSecureRandomProvider extends Provider {
        private static final String NAME = "ForgeStrongDRBGProvider";
        private static final String ALGORITHM = "ForgeStrongDRBG";

        public StrongSecureRandomProvider() {
            super(NAME, "1.0", "Strong SecureRandom provider for DRBG coverage tests");
            put("SecureRandom." + ALGORITHM, StrongSecureRandomSpi.class.getName());
        }
    }

    public static final class StrongSecureRandomSpi extends SecureRandomSpi {
        private static final AtomicInteger seedRequests = new AtomicInteger(0);
        private int nextValue;

        @Override
        protected void engineSetSeed(byte[] seed) {
            nextValue += seed.length;
        }

        @Override
        protected void engineNextBytes(byte[] bytes) {
            fill(bytes);
        }

        @Override
        protected byte[] engineGenerateSeed(int numBytes) {
            seedRequests.incrementAndGet();
            byte[] seed = new byte[numBytes];
            fill(seed);
            return seed;
        }

        private void fill(byte[] bytes) {
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte)(nextValue++ & 0xff);
            }
        }
    }

    private static void restoreSystemProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }

    private static void restoreSecurityProperty(String name, String previousValue) {
        if (previousValue == null) {
            Security.setProperty(name, "");
        } else {
            Security.setProperty(name, previousValue);
        }
    }
}

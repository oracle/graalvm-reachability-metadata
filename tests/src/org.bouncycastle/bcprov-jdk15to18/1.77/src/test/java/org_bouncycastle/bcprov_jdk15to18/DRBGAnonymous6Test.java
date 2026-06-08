/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Security;

import org.bouncycastle.crypto.prng.ThreadedSeedGenerator;
import org.bouncycastle.jcajce.provider.drbg.DRBG;
import org.junit.jupiter.api.Test;

public class DRBGAnonymous6Test {
    private static final String ENTROPY_SOURCE_PROPERTY = "org.bouncycastle.drbg.entropysource";

    @Test
    void defaultSecureRandomRejectsConfiguredClassThatIsNotEntropySourceProvider() {
        String previousSystemEntropySource = System.getProperty(ENTROPY_SOURCE_PROPERTY);
        String previousSecurityEntropySource = Security.getProperty(ENTROPY_SOURCE_PROPERTY);
        System.setProperty(ENTROPY_SOURCE_PROPERTY, ThreadedSeedGenerator.class.getName());
        if (previousSecurityEntropySource != null) {
            Security.setProperty(ENTROPY_SOURCE_PROPERTY, ThreadedSeedGenerator.class.getName());
        }

        try {
            ExceptionInInitializerError thrown = assertThrows(ExceptionInInitializerError.class,
                () -> new ExposedDefault());

            IllegalStateException cause = assertInstanceOf(IllegalStateException.class, thrown.getCause());
            assertTrue(cause.getMessage().contains("entropy source " + ThreadedSeedGenerator.class.getName()
                + " not created"));
        } finally {
            restoreSystemProperty(ENTROPY_SOURCE_PROPERTY, previousSystemEntropySource);
            if (previousSecurityEntropySource != null) {
                restoreSecurityProperty(ENTROPY_SOURCE_PROPERTY, previousSecurityEntropySource);
            }
        }
    }

    private static final class ExposedDefault extends DRBG.Default {
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

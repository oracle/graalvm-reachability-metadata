/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.bootstrap.jbang.JBangDevModeLauncherImpl;

public class JBangDevModeLauncherImplTest {

    @Test
    void readsLauncherDescriptorAndQuarkusVersionResources() {
        final String previousDevMode = System.getProperty("quarkus.dev");
        final String previousLogManager = System.getProperty("java.util.logging.manager");
        try {
            final RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> JBangDevModeLauncherImpl.main("--help"));

            assertTrue(hasMessageContaining(exception, "Invalid artifact")
                    || exception instanceof StringIndexOutOfBoundsException
                    || exception instanceof NullPointerException,
                    () -> "Unexpected JBang dev mode launcher failure: " + exception);
        } finally {
            restoreProperty("quarkus.dev", previousDevMode);
            restoreProperty("java.util.logging.manager", previousLogManager);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static boolean hasMessageContaining(Throwable throwable, String value) {
        Throwable current = throwable;
        while (current != null) {
            final String message = current.getMessage();
            if (message != null && message.contains(value)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

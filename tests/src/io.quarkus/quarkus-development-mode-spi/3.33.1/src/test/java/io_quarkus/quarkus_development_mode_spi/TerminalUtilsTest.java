/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_development_mode_spi;

import java.io.Console;

import io.quarkus.dev.console.TerminalUtils;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import static org.assertj.core.api.Assertions.assertThat;

public class TerminalUtilsTest {

    private static final String FORCE_COLOR_SUPPORT = "io.quarkus.force-color-support";
    private static final String JDK_CONSOLE = "jdk.console";

    @Test
    void nullConsoleIsNotATerminal() {
        assertThat(TerminalUtils.isTerminal(null)).isFalse();
    }

    @Test
    void allocatedConsoleCanBeCheckedForTerminalSupport() {
        Console console = new ObjenesisStd().newInstance(Console.class);

        boolean expectedTerminal = Runtime.version().feature() < 22 || System.console() != null;

        assertThat(TerminalUtils.isTerminal(console)).isEqualTo(expectedTerminal);
    }

    @Test
    void forcedColorSupportDoesNotRequireAnAttachedConsole() {
        String originalForceColorSupport = System.getProperty(FORCE_COLOR_SUPPORT);
        String originalJdkConsole = System.getProperty(JDK_CONSOLE);
        try {
            System.setProperty(FORCE_COLOR_SUPPORT, "true");
            System.clearProperty(JDK_CONSOLE);

            assertThat(TerminalUtils.hasColorSupport()).isTrue();
            assertThat(System.getProperty(JDK_CONSOLE)).isEqualTo("java.base");
        } finally {
            restoreProperty(FORCE_COLOR_SUPPORT, originalForceColorSupport);
            restoreProperty(JDK_CONSOLE, originalJdkConsole);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}

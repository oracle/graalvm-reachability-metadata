/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jline.jline_terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

public class TerminalBuilderTest {
    private static final String TERMINAL_NAME = "coverage-terminal";

    @Test
    public void systemBuilderFallsBackToDumbTerminalWithParentProcessColorDetection() throws Exception {
        String previousDumbColor = System.getProperty(TerminalBuilder.PROP_DUMB_COLOR);
        System.setProperty(TerminalBuilder.PROP_DUMB_COLOR, "false");
        try (Terminal terminal = TerminalBuilder.builder()
                .name(TERMINAL_NAME)
                .system(true)
                .jna(false)
                .jansi(false)
                .exec(false)
                .dumb(true)
                .build()) {
            assertThat(terminal.getName()).isEqualTo(TERMINAL_NAME);
            assertThat(terminal.getType()).startsWith(Terminal.TYPE_DUMB);
        } finally {
            restoreProperty(TerminalBuilder.PROP_DUMB_COLOR, previousDumbColor);
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

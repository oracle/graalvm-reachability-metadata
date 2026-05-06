/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JLineTerminalBuilderTest {
    private static final String DUMB_PROPERTY = "org.jline.terminal.dumb";
    private static final String DUMB_COLOR_PROPERTY = "org.jline.terminal.dumb.color";

    @Test
    void systemDumbTerminalInspectsParentProcessCommand() throws Exception {
        String originalDumb = System.getProperty(DUMB_PROPERTY);
        String originalDumbColor = System.getProperty(DUMB_COLOR_PROPERTY);

        try {
            System.clearProperty(DUMB_PROPERTY);
            System.clearProperty(DUMB_COLOR_PROPERTY);

            Terminal terminal = TerminalBuilder.builder()
                    .name("parent process command terminal")
                    .provider("dumb")
                    .system(true)
                    .nativeSignals(false)
                    .build();
            try {
                assertThat(terminal.getName()).isEqualTo("parent process command terminal");
                assertThat(terminal.getType()).startsWith(Terminal.TYPE_DUMB);
            } finally {
                terminal.close();
            }
        } finally {
            restoreProperty(DUMB_PROPERTY, originalDumb);
            restoreProperty(DUMB_COLOR_PROPERTY, originalDumbColor);
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

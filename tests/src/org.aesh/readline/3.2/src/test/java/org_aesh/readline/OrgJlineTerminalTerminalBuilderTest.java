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

public class OrgJlineTerminalTerminalBuilderTest {
    @Test
    void dumbSystemTerminalChecksParentProcessCommand() throws Exception {
        String originalDumb = System.getProperty(TerminalBuilder.PROP_DUMB);
        String originalDumbColor = System.getProperty(TerminalBuilder.PROP_DUMB_COLOR);
        try {
            System.clearProperty(TerminalBuilder.PROP_DUMB);
            System.clearProperty(TerminalBuilder.PROP_DUMB_COLOR);

            Terminal terminal = TerminalBuilder.builder()
                    .name("jline dumb system terminal")
                    .type("dumb")
                    .system(true)
                    .nativeSignals(false)
                    .build();
            try {
                assertThat(terminal.getName()).isEqualTo("jline dumb system terminal");
                assertThat(terminal.getType()).startsWith("dumb");
            } finally {
                terminal.close();
            }
        } finally {
            restoreProperty(TerminalBuilder.PROP_DUMB, originalDumb);
            restoreProperty(TerminalBuilder.PROP_DUMB_COLOR, originalDumbColor);
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

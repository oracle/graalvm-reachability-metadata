/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.Size;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class TerminalFactoryTest {

    @Test
    void dumbTerminalKeepsConfiguredNameTypeAndSize() throws Exception {
        DumbTerminal terminal = new DumbTerminal(
                "terminal-test",
                "ansi",
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8.name());

        try {
            Size size = new Size(120, 40);
            terminal.setSize(size);

            assertThat(terminal.getName()).isEqualTo("terminal-test");
            assertThat(terminal.getType()).isEqualTo("ansi");
            assertThat(terminal.getSize().getColumns()).isEqualTo(120);
            assertThat(terminal.getSize().getRows()).isEqualTo(40);
        } finally {
            terminal.reader().close();
            terminal.close();
        }
    }
}

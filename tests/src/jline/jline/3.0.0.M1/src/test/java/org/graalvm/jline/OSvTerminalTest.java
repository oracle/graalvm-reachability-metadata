/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class OSvTerminalTest {

    @Test
    void terminalMaintainsConfiguredSizeAndEchoState() throws Exception {
        try (Terminal terminal = TerminalBuilder.builder()
                .name("sized-terminal")
                .type("ansi")
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .size(new Size(40, 10))
                .build()) {
            terminal.setSize(new Size(100, 30));

            assertThat(terminal.getSize().getColumns()).isEqualTo(100);
            assertThat(terminal.getSize().getRows()).isEqualTo(30);

            boolean previousEcho = terminal.echo();
            terminal.echo(!previousEcho);

            assertThat(terminal.echo()).isEqualTo(!previousEcho);
        }
    }
}

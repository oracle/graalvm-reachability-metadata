/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TerminalFactoryTest {

    @Test
    @Timeout(10)
    void terminalBuilderCreatesANonSystemTerminalWithConfiguredAttributesAndSize() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Attributes attributes = new Attributes();
        attributes.setLocalFlag(Attributes.LocalFlag.ECHO, true);
        Size size = new Size(132, 43);

        try (Terminal terminal = TerminalBuilder.builder()
                .name("external-terminal-test")
                .type("ansi")
                .streams(input, output)
                .system(false)
                .build()) {
            terminal.setAttributes(attributes);
            terminal.setSize(size);
            terminal.writer().print("terminal output");
            terminal.flush();

            assertThat(terminal.getName()).isEqualTo("external-terminal-test");
            assertThat(terminal.getType()).isEqualTo("ansi");
            assertThat(terminal.echo()).isTrue();
            assertThat(terminal.getSize().getColumns()).isEqualTo(132);
            assertThat(terminal.getSize().getRows()).isEqualTo(43);
            assertThat(output.toString(StandardCharsets.UTF_8.name())).contains("terminal output");
        }
    }

    @Test
    @Timeout(10)
    void terminalSignalHandlersCanBeRegisteredAndRaised() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<Terminal.Signal> handledSignals = new ArrayList<Terminal.Signal>();

        try (Terminal terminal = TerminalBuilder.builder()
                .name("signal-terminal-test")
                .type("ansi")
                .streams(input, output)
                .system(false)
                .build()) {
            Terminal.SignalHandler previous = terminal.handle(Terminal.Signal.WINCH, handledSignals::add);

            terminal.raise(Terminal.Signal.WINCH);

            assertThat(previous).isNotNull();
            assertThat(handledSignals).containsExactly(Terminal.Signal.WINCH);
        }
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class TerminalFactoryTest {

    @Test
    void terminalBuilderCreatesStreamBackedTerminalWithConfiguredNameAndType() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (Terminal terminal = TerminalBuilder.builder()
                .name("metadata-terminal")
                .type("ansi")
                .streams(new ByteArrayInputStream(new byte[0]), output)
                .build()) {
            assertThat(terminal.getName()).isEqualTo("metadata-terminal");
            assertThat(terminal.getType()).isEqualTo("ansi");
            assertThat(terminal.getStringCapability(InfoCmp.Capability.cursor_up)).isNotNull();

            terminal.writer().print("hello terminal");
            terminal.flush();
        }

        assertThat(output.toString(StandardCharsets.UTF_8.name())).contains("hello terminal");
    }

    @Test
    void terminalRaisesRegisteredSignalHandlers() throws Exception {
        AtomicReference<Terminal.Signal> handledSignal = new AtomicReference<Terminal.Signal>();

        try (Terminal terminal = TerminalBuilder.builder()
                .name("signal-terminal")
                .type("ansi")
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .build()) {
            Terminal.SignalHandler previousHandler = terminal.handle(Terminal.Signal.INT, handledSignal::set);

            terminal.raise(Terminal.Signal.INT);

            assertThat(previousHandler).isSameAs(Terminal.SignalHandler.SIG_DFL);
            assertThat(handledSignal).hasValue(Terminal.Signal.INT);
        }
    }
}

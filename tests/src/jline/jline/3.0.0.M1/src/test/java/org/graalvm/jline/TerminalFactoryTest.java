/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.ExternalTerminal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class TerminalFactoryTest {

    @Test
    void builderCreatesATerminalWithTheConfiguredNameTypeAndStreams() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (Terminal terminal = TerminalBuilder.builder()
                .name("builder-terminal")
                .type("ansi")
                .streams(new ByteArrayInputStream(new byte[0]), output)
                .system(false)
                .build()) {
            terminal.writer().print("hello from jline");
            terminal.flush();

            assertThat(terminal.getName()).isEqualTo("builder-terminal");
            assertThat(terminal.getType()).isEqualTo("ansi");
            assertThat(output.toString(StandardCharsets.UTF_8)).contains("hello from jline");
        }
    }

    @Test
    void terminalInvokesRegisteredSignalHandlers() throws Exception {
        AtomicReference<Terminal.Signal> receivedSignal = new AtomicReference<Terminal.Signal>();

        try (PipedInputStream terminalInput = new PipedInputStream();
             PipedOutputStream inputWriter = new PipedOutputStream(terminalInput);
             ByteArrayOutputStream terminalOutput = new ByteArrayOutputStream();
             Terminal terminal = new ExternalTerminal(
                     "signal-terminal",
                     "ansi",
                     terminalInput,
                     terminalOutput,
                     StandardCharsets.UTF_8.name())) {
            terminal.handle(Terminal.Signal.INT, receivedSignal::set);

            terminal.raise(Terminal.Signal.INT);

            assertThat(receivedSignal).hasValue(Terminal.Signal.INT);
            inputWriter.close();
        }
    }
}

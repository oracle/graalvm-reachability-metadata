/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.ExternalTerminal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsoleReaderTest {

    @Test
    void readLineReadsInputThroughAnExternalTerminalAndStoresItInHistory() throws Exception {
        try (PipedInputStream terminalInput = new PipedInputStream();
             PipedOutputStream inputWriter = new PipedOutputStream(terminalInput);
             ByteArrayOutputStream terminalOutput = new ByteArrayOutputStream();
             Terminal terminal = new ExternalTerminal(
                     "console-reader-test",
                     "ansi",
                     terminalInput,
                     terminalOutput,
                     StandardCharsets.UTF_8.name())) {
            LineReader reader = LineReaderBuilder.builder()
                    .appName("console-reader-test")
                    .terminal(terminal)
                    .build();

            inputWriter.write("hello world\n".getBytes(StandardCharsets.UTF_8));
            inputWriter.flush();

            String line = reader.readLine("prompt> ");

            assertThat(line).isEqualTo("hello world");
            assertThat(reader.getHistory().size()).isEqualTo(1);
            assertThat(reader.getHistory().get(0)).isEqualTo("hello world");
            assertThat(terminalOutput.toString(StandardCharsets.UTF_8)).contains("prompt> ");
        }
    }
}

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
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsoleReaderTest {

    @Test
    @Timeout(10)
    void lineReaderReadsALineFromTheConfiguredTerminalStreams() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream("alpha beta\n".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (Terminal terminal = TerminalBuilder.builder()
                .name("line-reader-test")
                .type("ansi")
                .streams(input, output)
                .system(false)
                .build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("line-reader-test")
                    .variable(LineReader.DISABLE_HISTORY, Boolean.TRUE)
                    .build();

            String line = reader.readLine("prompt> ");

            assertThat(line).isEqualTo("alpha beta");
            assertThat(reader.getTerminal()).isSameAs(terminal);
            assertThat(terminal.getName()).isEqualTo("line-reader-test");
            assertThat(terminal.getType()).isEqualTo("ansi");
            assertThat(output.toString(StandardCharsets.UTF_8.name())).contains("prompt> ");
        }
    }
}

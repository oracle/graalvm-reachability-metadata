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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsoleReaderTest {

    @Test
    void lineReaderReadsInputFromTheConfiguredTerminal() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (Terminal terminal = TerminalBuilder.builder()
                .name("line-reader-terminal")
                .type("ansi")
                .streams(new ByteArrayInputStream("hello world\n".getBytes(StandardCharsets.UTF_8)), output)
                .build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("metadata-line-reader")
                    .variable(LineReader.DISABLE_HISTORY, Boolean.TRUE)
                    .build();

            String line = reader.readLine("prompt> ");

            assertThat(line).isEqualTo("hello world");
            assertThat(reader.getTerminal()).isSameAs(terminal);
            assertThat(reader.getVariable(LineReader.DISABLE_HISTORY)).isEqualTo(Boolean.TRUE);
        }

        assertThat(output.toString(StandardCharsets.UTF_8.name())).contains("prompt> ");
    }
}

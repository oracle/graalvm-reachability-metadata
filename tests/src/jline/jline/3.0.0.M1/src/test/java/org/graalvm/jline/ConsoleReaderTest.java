/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.history.history.MemoryHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.ExternalTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsoleReaderTest {

    @Test
    @Timeout(10)
    void lineReaderReadsScriptedInputAndRecordsHistory() throws Exception {
        byte[] input = "hello native image\n".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        History history = new MemoryHistory();

        try (Terminal terminal = new ExternalTerminal(
                "line-reader-test",
                "ansi",
                new ByteArrayInputStream(input),
                output,
                StandardCharsets.UTF_8.name())) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("jline3-test")
                    .history(history)
                    .variable(LineReader.LIST_MAX, 25)
                    .build();

            String line = reader.readLine("prompt> ");

            assertThat(line).isEqualTo("hello native image");
            assertThat(reader.getTerminal()).isSameAs(terminal);
            assertThat(reader.getVariable(LineReader.LIST_MAX)).isEqualTo(25);
            assertThat(history).hasSize(1);
            assertThat(history.get(0)).isEqualTo("hello native image");
        }

        assertThat(output.toString(StandardCharsets.UTF_8.name())).contains("prompt> ");
    }
}
